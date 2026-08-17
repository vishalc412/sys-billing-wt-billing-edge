package com.westfiled.api.billing.configs.interceptors;

import com.westfiled.api.billing.entities.ErrorResponse;
import com.westfiled.api.billing.soap.exceptions.DownStreamServiceException;
import com.westfiled.api.billing.soap.exceptions.NoContentFoundException;
import com.westfiled.api.billing.soap.exceptions.SamlTokenException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Central error mapping, replacing the Mule error_Flow sub-flow shared by every implementation
 * flow. Controllers never catch exceptions themselves (see controller-standards.md).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoContentFoundException.class)
    public ResponseEntity<Void> handleNoContent(NoContentFoundException ex) {
        log.info("No content: {}", ex.getMessage());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler({ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse("BAD_REQUEST", ex));
    }

    @ExceptionHandler(SamlTokenException.class)
    public ResponseEntity<ErrorResponse> handleSamlTokenException(SamlTokenException ex) {
        log.error("SAML token error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorResponse("SAML_TOKEN_ERROR", ex));
    }

    @ExceptionHandler(DownStreamServiceException.class)
    public ResponseEntity<ErrorResponse> handleDownStreamServiceException(DownStreamServiceException ex) {
        log.error("Downstream service error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorResponse("DOWNSTREAM_SERVICE_ERROR", ex));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse("INTERNAL_ERROR", ex));
    }

    private ErrorResponse errorResponse(String errorType, Exception ex) {
        return ErrorResponse.builder()
                .faultActor("sys-billing")
                .errorDesc(ex.getMessage())
                .errorType(errorType)
                .errorCause(ex.getCause() == null ? null : ex.getCause().getMessage())
                .correlationId(MDC.get(RequestResponseLoggingFilter.CORRELATION_ID_MDC_KEY))
                .build();
    }
}
