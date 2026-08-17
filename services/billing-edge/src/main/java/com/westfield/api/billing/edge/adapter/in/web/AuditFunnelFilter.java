package com.westfield.api.billing.edge.adapter.in.web;

import com.westfield.api.billing.edge.application.failure.UnhandledFailurePresenter;
import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.platform.observability.MigratedFrom;
import com.westfield.api.billing.platform.spi.AuditRecordPort;
import com.westfield.api.billing.platform.spi.CallerContext;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * The one funnel every request passes through: build the audit record, route, complete the audit
 * record with the outcome (N-0022, N-0050, N-0048).
 *
 * <p><b>Nothing bypasses the audit trail</b> except the console, which the legacy also leaves
 * unaudited because its flow never calls {@code setRequestResponse} (N-0031 ec 2, N-0050 ec 1).
 * ADR-0038 does not add auditing for it: that would be an audit-stream change smuggled in under a
 * security fix.
 *
 * <p>Two failure semantics here are the opposite of the posture everywhere else in this application,
 * and both are deliberate:
 * <ul>
 *   <li>a failure BUILDING the audit record fails the whole request before any business logic runs.
 *       The legacy makes {@code setRequestResponse} a {@code <flow>} rather than a {@code <sub-flow>}
 *       precisely so that it has its own error scope (N-0050 ec 2). A Spring implementer's instinct
 *       is to make logging non-fatal; that instinct would silently remove an audit guarantee
 *       (ADR-0017);</li>
 *   <li>a failure COMPLETING the record surfaces as an error rather than being absorbed by any
 *       caller's handler, for the same structural reason (N-0048 ec 2).</li>
 * </ul>
 *
 * <p>Ordering is corrected here, once, per ADR-0035: the record is completed AFTER the final status
 * is known, on all paths. The legacy runs the audit flow BEFORE the shared error handler on the
 * catch-all path only, so the one path whose outcome is least understood is the one the audit trail
 * fails to record. The signal that lost null status accidentally provided — "this went through the
 * catch-all" — is replaced deliberately by the explicit {@code unhandledFailure} marker.
 */
@MigratedFrom(value = "km:node/N-0022",
        note = "sapi-billing-search-main: setRequestResponse, route, responseLogFlow; ADR-0035 ordering")
public class AuditFunnelFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(AuditFunnelFilter.class);

    private final BillingEdgeProperties properties;
    private final AuditRecordPort auditRecord;
    private final CallerContextFactory callerContextFactory;
    private final UnhandledFailurePresenter presenter;
    private final FailureResponseWriter failureWriter;
    private final MeterRegistry meterRegistry;

    public AuditFunnelFilter(BillingEdgeProperties properties,
                             AuditRecordPort auditRecord,
                             CallerContextFactory callerContextFactory,
                             UnhandledFailurePresenter presenter,
                             FailureResponseWriter failureWriter,
                             MeterRegistry meterRegistry) {
        this.properties = properties;
        this.auditRecord = auditRecord;
        this.callerContextFactory = callerContextFactory;
        this.presenter = presenter;
        this.failureWriter = failureWriter;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (ConsolePaths.isConsole(request, properties)) {
            // Unaudited, exactly as today (N-0031 ec 2). Preserved by ADR-0038.
            chain.doFilter(request, response);
            return;
        }

        CallerContext caller = callerContextFactory.fromSecurityContext();
        RequestFunnel.putCaller(request, caller);

        try {
            auditRecord.open(rawUri(request), caller, request.getHeader(RequestFunnel.INTERACTION_CORRELATION_ID_HEADER));
            RequestFunnel.markAudited(request);
        } catch (RuntimeException auditConstructionFailed) {
            // No business logic runs. This is the audit guarantee, not a logging convenience.
            LOG.error("Audit record construction failed; the request is failed before routing "
                    + "(N-0050 ec 2, ADR-0017)", auditConstructionFailed);
            meterRegistry.counter("billing.audit.construction_failed").increment();
            failureWriter.write(request, response,
                    presenter.present(auditConstructionFailed, RequestFunnel.correlationId(request), null));
            return;
        }

        boolean unhandledFailure = false;
        Throwable failure = null;
        try {
            chain.doFilter(request, response);
        } catch (Exception routingFailure) {
            unhandledFailure = true;
            failure = routingFailure;
        }

        int status;
        if (failure == null) {
            status = response.getStatus();
        } else {
            // The status is DECIDED first, then the record is completed, then the body is written.
            // ADR-0035 requires the record to carry the status the caller actually received;
            // ERR-003-c requires the record to be completed before the presentation is produced.
            // Deciding, recording, then writing satisfies both without reordering anything else.
            status = RequestFunnel.failureStatus(response);
        }

        try {
            auditRecord.close(status, unhandledFailure);
        } catch (RuntimeException auditCompletionFailed) {
            // Not absorbed by anybody's handler (N-0048 ec 2). It becomes the response.
            LOG.error("Audit record completion failed", auditCompletionFailed);
            meterRegistry.counter("billing.audit.completion_failed").increment();
            if (!response.isCommitted()) {
                failureWriter.write(request, response,
                        presenter.present(auditCompletionFailed, RequestFunnel.correlationId(request), null));
            }
            return;
        }

        if (failure != null && !response.isCommitted()) {
            failureWriter.write(request, response,
                    presenter.present(failure, RequestFunnel.correlationId(request), statusIfUpstream(response)));
        }
    }

    /**
     * The status the funnel already holds is passed to the presenter as the "upstream" status so the
     * derived status matches what the caller is told. When nothing decided a status, this is null
     * and {@code FailureStatusRule} answers 500.
     */
    private static Integer statusIfUpstream(HttpServletResponse response) {
        int current = response.getStatus();
        return current == 0 || current == RequestFunnel.DEFAULT_SUCCESS_STATUS ? null : current;
    }

    /** The raw URI as received, query string included: the audit record keeps it unmasked (N-0051). */
    private static String rawUri(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }
}
