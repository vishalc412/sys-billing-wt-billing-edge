package com.westfield.api.billing.edge.adapter.in.web;

import com.westfield.api.billing.edge.application.port.AuditEventSink;
import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.edge.domain.api.ApiResource;
import com.westfield.api.billing.edge.domain.api.ApiResourceTable;
import com.westfield.api.billing.edge.domain.audit.EndpointAuditRecord;
import com.westfield.api.billing.platform.observability.MigratedFrom;
import com.westfield.api.billing.platform.observability.TracePoint;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * The per-endpoint audit record, emitted at the start of each externally-facing endpoint (N-0036).
 *
 * <p>ADR-0017 consolidates the legacy's FIVE verbatim copies of this projection into one
 * implementation with the URI template as a parameter. No emitted value changes; five copies can no
 * longer drift apart, which is exactly how the two impersonation definitions came to disagree in the
 * first place.
 *
 * <p>The gap is preserved on purpose: only five of the eight endpoints emit this record. The three
 * {@code /primaryAccount*} resources emit none (N-0045 ec 1), while still emitting a request/response
 * record. Closing that gap would change log volume and any report that counts records, and ADR-0017
 * gates it on a compliance owner nobody has identified (R-021).
 */
@MigratedFrom(value = "km:node/N-0036", note = "EndpointLogger at the head of five endpoint flows")
public class EndpointAuditFilter extends OncePerRequestFilter {

    private final ApiResourceTable table;
    private final AuditEventSink sink;
    private final BillingEdgeProperties properties;

    public EndpointAuditFilter(ApiResourceTable table, AuditEventSink sink, BillingEdgeProperties properties) {
        this.table = table;
        this.sink = sink;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Optional<ApiResource> resource = table.find(request.getMethod(), request.getRequestURI());
        if (resource.isPresent() && resource.get().emitsEndpointAuditRecord()) {
            Map<String, Object> fields = EndpointAuditRecord.fields(
                    request.getHeader("x-calling-system"),
                    properties.getApi().getName(),
                    properties.getApi().getVersion(),
                    request.getMethod(),
                    rawUri(request),
                    resource.get().template(),
                    request.getHeader(RequestFunnel.API_KEY_HEADER),
                    RequestFunnel.caller(request));
            sink.emit(TracePoint.START, RequestFunnel.correlationId(request), fields);
        }
        chain.doFilter(request, response);
    }

    private static String rawUri(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }
}
