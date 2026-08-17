package com.westfield.api.billing.edge.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westfield.api.billing.edge.domain.api.ContractViolation;
import com.westfield.api.billing.platform.observability.MigratedFrom;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.Map;

/**
 * The six contract-violation responses, written exactly as the legacy writes them
 * (N-0024 … N-0029).
 *
 * <p>One field, a fixed string, no diagnostic. A caller cannot tell which validation failed, and
 * that opacity is preserved (ADR-0042): adding detail is a contract change, and the published
 * {@code commonError} type these responses are documented under has never been what the application
 * emits.
 *
 * <p>The 405 carries NO {@code Allow} header, which HTTP requires (N-0026 ec). Preserved; Spring
 * sets one by default, so it is removed on purpose rather than never added.
 */
@MigratedFrom(value = "km:node/N-0024", note = "the six APIkit handlers; exact bodies per ADR-0042")
public class ContractViolationWriter {

    private final ObjectMapper objectMapper;

    public ContractViolationWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, ContractViolation violation) throws IOException {
        response.setStatus(violation.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // A 405 is written with NO Allow header, which HTTP requires and the legacy omits (N-0026 ec).
        // It is omitted by never adding it: this writer bypasses Spring's dispatcher entirely, so
        // nothing else is in a position to add one. (Do not "clear" it by setting the header to null
        // — a servlet container is not required to accept a null header value, and the previous
        // attempt to do that would have thrown before the body was written.)
        response.getWriter().write(objectMapper.writeValueAsString(body(violation)));
        response.getWriter().flush();
    }

    /** The single-field shape. Deliberately not the published multi-field fault type (ADM-003-g). */
    public static Map<String, Object> body(ContractViolation violation) {
        return Map.of("message", violation.message());
    }
}
