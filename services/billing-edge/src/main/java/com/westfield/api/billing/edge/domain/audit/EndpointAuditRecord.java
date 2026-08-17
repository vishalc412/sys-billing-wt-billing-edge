package com.westfield.api.billing.edge.domain.audit;

import com.westfield.api.billing.platform.observability.MigratedFrom;
import com.westfield.api.billing.platform.spi.CallerContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The per-endpoint audit record emitted at the start of each externally-facing endpoint (N-0036).
 *
 * <p>The legacy carries FIVE verbatim copies of this projection — one per endpoint, differing only
 * in the {@code uriTemplate} literal. ADR-0017 consolidates them into this one class with the
 * template as a parameter; no emitted value changes, and five copies can no longer drift apart.
 *
 * <p>Two facts are preserved rather than tidied:
 * <ul>
 *   <li>It uses the INCOMPLETE impersonation rule ({@code !isEmpty(act)}), which disagrees with the
 *       request/response stream for flat-{@code actSub} tokens (ADR-0036, N-0052 defect).</li>
 *   <li>The {@code x-api-key} header is captured even though no API-key policy protects this service
 *       anywhere in source; it is presumably supplied by an upstream experience API (N-0036 ec 5).
 *       Dropping it would silently remove a field a log consumer may key on.</li>
 * </ul>
 *
 * <p>Only five of the eight endpoints emit this record. The three {@code /primaryAccount*} resources
 * do not, and ADR-0017 preserves that gap rather than closing it.
 */
@MigratedFrom(value = "km:node/N-0036", note = "EndpointLogger projection; five copies collapsed to one (ADR-0017)")
public final class EndpointAuditRecord {

    private EndpointAuditRecord() {
    }

    @MigratedFrom(value = "km:node/N-0036", note = "identical field set for every endpoint; only uriTemplate differs")
    public static Map<String, Object> fields(String callingSystem,
                                             String apiName,
                                             String apiVersion,
                                             String httpMethod,
                                             String requestUri,
                                             String uriTemplate,
                                             String apiKey,
                                             CallerContext caller) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("callingSystem", orEmpty(callingSystem));
        fields.put("apiName", apiName);
        fields.put("apiVersion", apiVersion);
        fields.put("sub", orEmpty(caller == null ? null : caller.subject()));
        fields.put("email", orEmpty(caller == null ? null : caller.email()));
        // No default here either, for the same reason as the request/response record (N-0036 ec 3).
        fields.put("clientId", caller == null ? null : caller.clientId());
        fields.put("httpMethod", httpMethod);
        fields.put("requestUri", requestUri);
        fields.put("uriTemplate", uriTemplate);
        fields.put("apiKey", orEmpty(apiKey));
        fields.put("impersonated", ImpersonationRule.actObjectOnly(caller));
        fields.put("actSub", orEmpty(caller == null ? null : caller.actorSubject()));
        fields.put("actEmail", orEmpty(caller == null ? null : caller.actorEmail()));
        return fields;
    }

    private static String orEmpty(String value) {
        return value == null || value.isEmpty() ? CallerContext.EMPTY : value;
    }
}
