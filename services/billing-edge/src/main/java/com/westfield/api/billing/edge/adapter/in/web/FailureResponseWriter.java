package com.westfield.api.billing.edge.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westfield.api.billing.edge.application.failure.UnhandledFailurePresenter;
import com.westfield.api.billing.edge.domain.failure.FailureStatusRule;
import com.westfield.api.billing.platform.observability.MigratedFrom;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;

/**
 * Writes a failure presentation to the wire (N-0030, ADR-0033).
 *
 * <p>Content-Type is set explicitly to {@code application/json}. The legacy sets none at all and
 * lets the media type be inferred from the DataWeave output directive (N-0023 ec 2); every path that
 * produces a body produces JSON, so stating it changes no observable value and removes an inference.
 *
 * <p>The {@code X-Billing-Failure-Origin} header is ADDITIVE — no legacy response carries it. It
 * exists because the back end's status is relayed verbatim (ADR-0033), so a caller receiving 401
 * cannot otherwise tell whose credentials were rejected.
 */
@MigratedFrom(value = "km:node/N-0030", note = "catch-all response writing; ADR-0033 origin header")
public class FailureResponseWriter {

    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public FailureResponseWriter(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    public void write(HttpServletRequest request,
                      HttpServletResponse response,
                      UnhandledFailurePresenter.Presentation presentation) throws IOException {
        if (FailureStatusRule.isRelayedAuthStatus(presentation.status())) {
            // The relay is preserved (ADR-0033) and counted, so the misleading status is measurable
            // before anyone argues about changing it.
            meterRegistry.counter("mule.parity.relayed_upstream_auth_status",
                    "status", String.valueOf(presentation.status())).increment();
        }
        if (presentation.status() == 404) {
            // A backend 404 for an account that does not exist becomes a 404 on an API resource that
            // does exist. Legacy behaviour (N-0068 defect), preserved and counted.
            meterRegistry.counter("mule.parity.relayed_upstream_not_found").increment();
        }
        response.setStatus(presentation.status());
        response.setHeader(RequestFunnel.FAILURE_ORIGIN_HEADER, headerValue(presentation));
        String body;
        try {
            body = objectMapper.writeValueAsString(presentation.body());
        } catch (RuntimeException | java.io.IOException presentationFailed) {
            // ERR-001-g. A failure INSIDE failure handling must not escape as an unhandled runtime
            // error: in the legacy this would raise inside a handler that has no further handler.
            // The caller gets a status attributable to this service, and the condition is counted so
            // that "our error handler is broken" is a number on a dashboard rather than a mystery.
            meterRegistry.counter("billing.failure.presentation_failed",
                    "error", presentationFailed.getClass().getSimpleName()).increment();
            response.setStatus(FailureStatusRule.NO_UPSTREAM_STATUS);
            response.setHeader(RequestFunnel.FAILURE_ORIGIN_HEADER, "this-service");
            writeBody(response, FailureStatusRule.NO_UPSTREAM_STATUS,
                    "{\"faultActor\":\"" + com.westfield.api.billing.edge.domain.failure.AbsorbedFailureBody.FAULT_ACTOR
                            + "\",\"errorDesc\":\"failure presentation failed\",\"errorType\":null,"
                            + "\"errorCause\":null,\"correlationId\":\""
                            + RequestFunnel.correlationId(request) + "\"}");
            return;
        }
        writeBody(response, presentation.status(), body);
    }

    /**
     * ADR-0031/ADR-0020: a 204 carries no body. The legacy leaves the fault content in place on the
     * 204 branch (N-0084 ec 4); RFC 9110 forbids a body on 204 and most clients discard it unseen.
     */
    static void writeBody(HttpServletResponse response, int status, String body) throws IOException {
        if (status == 204) {
            response.setContentLength(0);
            return;
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(body);
        response.getWriter().flush();
    }

    private static String headerValue(UnhandledFailurePresenter.Presentation presentation) {
        return presentation.origin().name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
