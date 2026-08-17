package com.westfield.api.billing.edge.domain.audit;

import com.westfield.api.billing.platform.observability.MigratedFrom;
import com.westfield.api.billing.platform.spi.CallerContext;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The request/response audit record: one line per request carrying who called, on whose behalf,
 * against which resource, and how it ended (N-0051 construction, N-0049 completion).
 *
 * <p>ADR-0017 makes the FIELD SET and the SENTINELS a contract, not an implementation detail. Five
 * verbatim copies of the projection became one class; no emitted value changed. In particular:
 * <ul>
 *   <li>absent values are the literal string {@code EMPTY}, never an omitted key, so log
 *       aggregation never sees a missing field;</li>
 *   <li>an absent agency-code list is an EMPTY LIST, not {@code EMPTY};</li>
 *   <li>{@code clientId} is the one field with no default. When the token context is absent it is
 *       null while every neighbour says {@code EMPTY} (N-0051 ec 1). That inconsistency is
 *       preserved: it is the only usable detector for "the token policy did not run".</li>
 * </ul>
 *
 * <p>{@code entryTimestamp} and {@code entryTimeMillis} are captured at slightly different points,
 * exactly as the legacy expression does (N-0051 ec 5). They are independent readings, and neither is
 * derived from the other, so a small difference between them is correct rather than a bug.
 */
@MigratedFrom(value = "km:node/N-0051", note = "requestResponseLog construction; ADR-0017 field contract")
public final class RequestResponseAuditRecord {

    /** Fixed marker identifying this log line as a request/response audit record (N-0051 rule 1). */
    public static final String IDENTIFIER = "UWSAPI-REQRES";

    /** The API layer this application sits in. Fixed literal in the legacy expression. */
    public static final String API_LAYER = "SAPI";

    /**
     * ADR-0035 replaces the legacy bare {@code null} with an explicit sentinel, consistent with the
     * {@code EMPTY} convention used by every neighbouring field, for the case where the status is
     * genuinely not known.
     */
    public static final String STATUS_UNKNOWN = CallerContext.EMPTY;

    private final OffsetDateTime entryTimestamp;
    private final long entryTimeMillis;
    private final String apiName;
    private final String apiVersion;
    private final String correlationId;
    private final String interactionCorrelationId;
    private final String requestUri;
    private final String requestTemplate;
    private final CallerContext caller;

    public RequestResponseAuditRecord(OffsetDateTime entryTimestamp,
                                      long entryTimeMillis,
                                      String apiName,
                                      String apiVersion,
                                      String correlationId,
                                      String interactionCorrelationId,
                                      String requestUri,
                                      CallerContext caller) {
        this.entryTimestamp = entryTimestamp;
        this.entryTimeMillis = entryTimeMillis;
        this.apiName = apiName;
        this.apiVersion = apiVersion;
        // Mule always supplies a correlation id, so the legacy `default "EMPTY"` branch is
        // unreachable (N-0051 ec 4). It is kept so the field can never be absent.
        this.correlationId = orEmpty(correlationId);
        this.interactionCorrelationId = orEmpty(interactionCorrelationId);
        this.requestUri = requestUri;
        // The legacy reads two DIFFERENT attributes here: requestUri (which carries the query
        // string) for the raw field, and requestPath (which does not) for the masked template.
        // Masking the query string as well would produce a template that no longer groups — every
        // distinct query would mask to a different shape — so the path is separated out first.
        this.requestTemplate = ResourcePathMasker.maskedTemplate(pathOf(requestUri));
        this.caller = caller;
    }

    public long entryTimeMillis() {
        return entryTimeMillis;
    }

    public String correlationId() {
        return correlationId;
    }

    /** The raw URI is retained in full; only {@code requestTemplate} is masked (N-0051 rule 3). */
    public String requestUri() {
        return requestUri;
    }

    public String requestTemplate() {
        return requestTemplate;
    }

    public CallerContext caller() {
        return caller;
    }

    /** The entry projection, in a stable key order so a log consumer sees one shape. */
    @MigratedFrom(value = "km:node/N-0051", note = "entry projection; EMPTY sentinels, null clientId")
    public Map<String, Object> entryFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("entryTimestamp", entryTimestamp == null ? null : entryTimestamp.toString());
        fields.put("entryTimeMillis", entryTimeMillis);
        fields.put("identifier", IDENTIFIER);
        fields.put("apiName", apiName);
        fields.put("apiVersion", apiVersion);
        fields.put("apiLayer", API_LAYER);
        fields.put("correlationId", correlationId);
        fields.put("interactionCorrelationId", interactionCorrelationId);
        fields.put("requestUri", requestUri);
        fields.put("requestTemplate", requestTemplate);
        fields.put("sub", orEmpty(caller == null ? null : caller.subject()));
        fields.put("email", orEmpty(caller == null ? null : caller.email()));
        // No default, deliberately. See the class comment and N-0051 ec 1.
        fields.put("clientId", caller == null ? null : caller.clientId());
        fields.put("agencyCodes", caller == null ? List.of() : List.copyOf(caller.agencyCodes()));
        fields.put("impersonated", ImpersonationRule.complete(caller));
        fields.put("actSub", orEmpty(caller == null ? null : caller.actorSubject()));
        fields.put("actEmail", orEmpty(caller == null ? null : caller.actorEmail()));
        return fields;
    }

    /**
     * The completed record: the entry projection plus the status actually returned, the wall-clock
     * elapsed time, and the ADR-0035 {@code unhandledFailure} marker.
     *
     * <p>Elapsed time preserves the legacy {@code default 0} on the entry timestamp (N-0049 ec 1):
     * when the entry millis were never populated, the elapsed figure is the full epoch-milliseconds
     * value — a nonsensical but NON-FAILING number. A broken audit record must never break the
     * request, so the arithmetic is left alone; the poisoned value is visible in dashboards and that
     * is the intended signal.
     *
     * @param responseStatusCode the status actually returned to the caller, or null when genuinely
     *                           undecided — rendered as the {@link #STATUS_UNKNOWN} sentinel
     */
    @MigratedFrom(value = "km:node/N-0049", note = "completion; ADR-0035 sentinel + unhandledFailure marker")
    public Map<String, Object> completedFields(Integer responseStatusCode,
                                               long exitTimeMillis,
                                               boolean unhandledFailure) {
        Map<String, Object> fields = entryFields();
        fields.put("responseStatusCode", responseStatusCode == null ? STATUS_UNKNOWN : responseStatusCode);
        fields.put("elapsedTime", exitTimeMillis - entryTimeMillis);
        fields.put("unhandledFailure", unhandledFailure);
        return fields;
    }

    /** The path half of a raw request URI: everything before the first {@code ?}. */
    private static String pathOf(String rawUri) {
        if (rawUri == null) {
            return "";
        }
        int query = rawUri.indexOf('?');
        return query < 0 ? rawUri : rawUri.substring(0, query);
    }

    private static String orEmpty(String value) {
        // The legacy `default "EMPTY"` idiom fires on an absent key AND on an explicit null
        // (N-0036 ec 2). A null-only check would not reproduce it, so both collapse here.
        return value == null || value.isEmpty() ? CallerContext.EMPTY : value;
    }
}
