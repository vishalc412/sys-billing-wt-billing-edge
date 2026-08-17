package com.westfield.api.billing.edge.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.edge.domain.security.AgencyEntitlementDecision;
import com.westfield.api.billing.edge.domain.security.AgencyEntitlementRule;
import com.westfield.api.billing.platform.errors.BillingProblem;
import com.westfield.api.billing.platform.observability.MigratedFrom;
import com.westfield.api.billing.platform.spi.CallerContext;
import com.westfield.api.billing.platform.spi.LegacyBehaviourFlags;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agency entitlement enforcement on the two worklist endpoints (ADR-0037, N-0035 ec 2).
 *
 * <p>This is the one place in this packet that implements behaviour the legacy does not have. The
 * legacy serves {@code /pastDueToday/{agencyCode}} and {@code /pendingCancelToday/{agencyCode}} to
 * any authenticated caller for any agency code, returning the named insured's name and postal
 * address, the policy number, the overdue amount and the cancellation date. The claim that would
 * authorise the request is read — and used only to write a log line.
 *
 * <p>It sits at the edge rather than inside the worklist implementations for the same reason the
 * legacy's own admission checks did: the check must be impossible to route around, and it must hold
 * whether or not an API Manager policy also performs it (R-003 was never answered).
 *
 * <p>403 is answered with an RFC 9457 problem detail. That is not a legacy shape — the legacy never
 * produced this response at all — and ADR-0042 reserves the problem shape for exactly that case.
 */
@MigratedFrom(value = "km:node/N-0035", note = "NEW behaviour per ADR-0037; deny on absent/empty claim")
public class AgencyEntitlementFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(AgencyEntitlementFilter.class);

    /** The two resources whose path parameter is an agency code (N-0039, N-0041). */
    private static final List<String> WORKLIST_PREFIXES = List.of("/pastDueToday/", "/pendingCancelToday/");

    private final AgencyEntitlementRule rule;
    private final LegacyBehaviourFlags flags;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public AgencyEntitlementFilter(AgencyEntitlementRule rule,
                                   LegacyBehaviourFlags flags,
                                   ObjectMapper objectMapper,
                                   MeterRegistry meterRegistry) {
        this.rule = rule;
        this.flags = flags;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String agencyCode = requestedAgencyCode(request.getRequestURI());
        if (agencyCode == null) {
            chain.doFilter(request, response);
            return;
        }
        CallerContext caller = RequestFunnel.caller(request);
        AgencyEntitlementDecision decision =
                rule.decide(agencyCode, caller, flags.enforceAgencyEntitlement());
        String clientId = caller == null || caller.clientId() == null ? "unknown" : caller.clientId();
        switch (decision) {
            case DENIED -> {
                meterRegistry.counter("billing.security.agency_entitlement_denied",
                        "clientId", clientId).increment();
                // No backend call is made: the response is written here and the chain stops.
                writeForbidden(request, response, agencyCode);
                return;
            }
            case UNENTITLED_SERVED -> {
                // log-only. A distinct counter, because "served without entitlement" is a different
                // fact from "denied" and the exposure has to be measurable while it persists.
                meterRegistry.counter("billing.security.agency_entitlement_unentitled_served",
                        "clientId", clientId).increment();
                LOG.warn("Unentitled agency worklist request SERVED in log-only mode "
                        + "(agencyCode={}, clientId={}) — ADR-0037", agencyCode, clientId);
            }
            case EXEMPT -> meterRegistry.counter("billing.security.agency_entitlement_exempt",
                    "clientId", clientId).increment();
            case ENTITLED -> {
                // Nothing to record: this is the expected path.
            }
        }
        chain.doFilter(request, response);
    }

    /** @return the {@code {agencyCode}} value, or null when this is not a worklist request. */
    static String requestedAgencyCode(String path) {
        if (path == null) {
            return null;
        }
        for (String prefix : WORKLIST_PREFIXES) {
            if (path.startsWith(prefix)) {
                String remainder = path.substring(prefix.length());
                int nextSeparator = remainder.indexOf('/');
                String code = nextSeparator < 0 ? remainder : remainder.substring(0, nextSeparator);
                return code.isEmpty() ? null : code;
            }
        }
        return null;
    }

    private void writeForbidden(HttpServletRequest request, HttpServletResponse response, String agencyCode)
            throws IOException {
        BillingProblem problem = new BillingProblem(
                URI.create("https://westfieldgrp.com/problems/agency-entitlement"),
                "Not entitled to this agency",
                403,
                "The access token does not carry agency code " + agencyCode + ".",
                URI.create(request.getRequestURI()),
                Map.of("correlationId", String.valueOf(RequestFunnel.correlationId(request))));
        response.setStatus(403);
        response.setContentType("application/problem+json");
        response.getWriter().write(objectMapper.writeValueAsString(asMap(problem)));
        response.getWriter().flush();
    }

    private static Map<String, Object> asMap(BillingProblem problem) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", problem.type().toString());
        body.put("title", problem.title());
        body.put("status", problem.status());
        body.put("detail", problem.detail());
        body.put("instance", problem.instance().toString());
        body.putAll(problem.extensions());
        return body;
    }
}
