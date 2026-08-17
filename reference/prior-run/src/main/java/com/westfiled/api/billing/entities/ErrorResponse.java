package com.westfiled.api.billing.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Shared error payload shape, mirroring the Mule error_Flow / sapi-common-errorhandler output
 * (faultActor, errorDesc, errorType, errorCause, correlationId).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private String faultActor;
    private String errorDesc;
    private String errorType;
    private String errorCause;
    private String correlationId;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
