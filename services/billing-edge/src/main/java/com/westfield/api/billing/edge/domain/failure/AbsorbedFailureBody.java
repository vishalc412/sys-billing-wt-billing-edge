package com.westfield.api.billing.edge.domain.failure;

import com.westfield.api.billing.platform.observability.MigratedFrom;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The uniform failure body every endpoint returns when it absorbs a backend or transformation
 * failure — the target's {@code error_Flow} / {@code defaultError} payload (N-0068, N-0069).
 *
 * <p>Four properties of this shape are preserved deliberately (ADR-0042):
 * <ul>
 *   <li>it does NOT match the {@code commonError} type the legacy RAML published. The application
 *       has never emitted that type; the contract was rewritten to match the code rather than the
 *       other way round;</li>
 *   <li>{@code errorCause} is emitted with a null VALUE when the cause is absent, not omitted. No
 *       field has a default (N-0069 ec 4), so a consumer always sees the same key set;</li>
 *   <li>{@code errorType} is a nested object of namespace and identifier, not a flat code string
 *       (N-0069 ec 3). Flattening it would change what a consumer parses;</li>
 *   <li>the raw cause is returned to the caller UNREDACTED. It can contain internal hostnames and
 *       fragments of the SOAP payload (N-0069 ec 2). ADR-0015 masks the LOGGING path and explicitly
 *       leaves the response body alone, because changing a response body is a contract change and
 *       belongs to ADR-0042, not to a logging decision.</li>
 * </ul>
 */
@MigratedFrom(value = "km:node/N-0069", note = "defaultError payload builder; shape frozen by ADR-0042")
public record AbsorbedFailureBody(
        String faultActor,
        String errorDesc,
        ErrorType errorType,
        String errorCause,
        String correlationId) {

    /**
     * The Mule error type as it serialises: a namespace and an identifier, never a flat string
     * (N-0069 ec 3).
     */
    public record ErrorType(String namespace, String identifier) {
    }

    /** The API naming itself as the fault actor, so support can attribute the failure (N-0069). */
    public static final String FAULT_ACTOR = "sapi-billing";

    @MigratedFrom(value = "km:node/N-0069", note = "key order and null-valued errorCause are the contract")
    public Map<String, Object> asMap() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("faultActor", faultActor);
        body.put("errorDesc", errorDesc);
        body.put("errorType", errorType == null ? null : nested(errorType));
        // Present with a null value, never omitted (N-0069 ec 4).
        body.put("errorCause", errorCause);
        body.put("correlationId", correlationId);
        return body;
    }

    /** LinkedHashMap, not Map.of: the key order is observable in the emitted JSON. */
    private static Map<String, Object> nested(ErrorType type) {
        Map<String, Object> rendered = new LinkedHashMap<>();
        rendered.put("namespace", type.namespace());
        rendered.put("identifier", type.identifier());
        return rendered;
    }
}
