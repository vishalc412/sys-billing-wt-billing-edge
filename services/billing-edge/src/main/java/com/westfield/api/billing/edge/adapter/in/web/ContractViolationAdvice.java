package com.westfield.api.billing.edge.adapter.in.web;

import com.westfield.api.billing.edge.domain.api.ApiResourceTable;
import com.westfield.api.billing.edge.domain.api.ContractViolation;
import com.westfield.api.billing.platform.observability.MigratedFrom;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/**
 * The backstop for the six contract-violation classes when the dispatcher, rather than the admission
 * filter, is the one that notices (N-0024 … N-0029).
 *
 * <p>The distinction that matters here is the one the legacy draws and HTTP does not: a path the
 * contract does not define is 404, while a path the contract DOES define with nothing implementing
 * it is <b>501</b> (N-0029). Spring answers 404 for both, so the resource table decides which of the
 * two applies. That is the safety net for spec-ahead-of-code drift, and it is the first thing to
 * fire when a resource is added to the contract without an implementation behind it.
 *
 * <p>It deliberately handles nothing else. A business failure is not a contract violation, and a
 * failure that no endpoint absorbed must reach the funnel's catch-all so that the audit record is
 * completed and the organisation-wide presentation produces the response (N-0030, ERR-003).
 */
@RestControllerAdvice
@MigratedFrom(value = "km:node/N-0025", note = "routing 404 vs declared-but-unimplemented 501")
public class ContractViolationAdvice {

    private final ApiResourceTable table;

    public ContractViolationAdvice(ApiResourceTable table) {
        this.table = table;
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    @MigratedFrom(value = "km:node/N-0029", note = "declared resource with no implementation is 501, not 404")
    public ResponseEntity<Map<String, Object>> noHandler(HttpServletRequest request) {
        ContractViolation violation = table.findByPath(request.getRequestURI()).isPresent()
                ? ContractViolation.NOT_IMPLEMENTED
                : ContractViolation.NOT_FOUND;
        return respond(violation);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> methodNotAllowed() {
        // No Allow header, which HTTP requires on a 405. Preserved (N-0026 ec).
        return respond(ContractViolation.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Map<String, Object>> notAcceptable() {
        return respond(ContractViolation.NOT_ACCEPTABLE);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> unsupportedMediaType() {
        return respond(ContractViolation.UNSUPPORTED_MEDIA_TYPE);
    }

    private static ResponseEntity<Map<String, Object>> respond(ContractViolation violation) {
        return ResponseEntity.status(violation.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ContractViolationWriter.body(violation));
    }
}
