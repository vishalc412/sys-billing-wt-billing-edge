package com.westfield.api.billing.edge.adapter.in.web;

import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.platform.observability.MigratedFrom;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Correlation identity for the main listener (N-0023).
 *
 * <p>Every response of the main API, success or failure, carries the correlation id back to the
 * caller. The CONSOLE deliberately does not (N-0032): its listener echoes nothing, and CON-001-d
 * asserts that difference rather than tidying it away.
 *
 * <p>Two identifiers are tracked, not one: this service's own correlation id, and the caller-supplied
 * interaction id that lets a business interaction spanning several API calls be reassembled
 * (N-0051 rule 2). They are separate fields on the audit record and neither substitutes for the
 * other.
 *
 * <p>This filter is outermost so that a request rejected by authentication still comes back with an
 * id support can search on.
 */
@MigratedFrom(value = "km:node/N-0023", note = "x-correlation-id echoed on success and failure alike")
public class CorrelationIdFilter extends OncePerRequestFilter {

    /** The MDC key every structured log entry is threaded through (N-0009). */
    public static final String MDC_KEY = "correlationId";

    private final BillingEdgeProperties properties;

    public CorrelationIdFilter(BillingEdgeProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlationId = firstNonBlank(
                request.getHeader(RequestFunnel.CORRELATION_ID_HEADER),
                UUID.randomUUID().toString());
        RequestFunnel.putCorrelationId(request, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            if (!ConsolePaths.isConsole(request, properties)) {
                // Set before the chain runs: an error path that commits the response early must not
                // lose the header (N-0023 — the same echo on success and failure).
                response.setHeader(RequestFunnel.CORRELATION_ID_HEADER, correlationId);
            }
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String firstNonBlank(String candidate, String fallback) {
        return candidate == null || candidate.isBlank() ? fallback : candidate;
    }
}
