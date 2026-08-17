package com.westfield.api.billing.edge.adapter.out.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westfield.api.billing.edge.application.port.AuditEventSink;
import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.edge.domain.audit.ResourcePathMasker;
import com.westfield.api.billing.platform.observability.MigratedFrom;
import com.westfield.api.billing.platform.observability.TracePoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Structured JSON logging with the legacy trace-point taxonomy (N-0009, N-0015).
 *
 * <p>Every entry is JSON, carries the correlation id, and carries a trace point drawn from
 * START / BEFORE_REQUEST / AFTER_REQUEST / EXCEPTION / END, so a request can be reconstructed end to
 * end from the log stream alone. Downstream analytics groups on those values, so the taxonomy is
 * reproduced exactly rather than modernised.
 *
 * <p><b>Masking is a deliberate divergence (ADR-0015).</b> The legacy defines
 * {@code json.data.mask.fields} and {@code json.data.disable.fields} and leaves them EMPTY in every
 * environment, so the SOAP request log points emit the full envelope INCLUDING THE SAML ASSERTION,
 * and the error log points emit the full fault payload (N-0009 ec 1, N-0101 ec 6). That is a
 * data-protection exposure that is invisible unless someone reads the property files. This
 * implementation ships non-empty defaults. It changes the log only: no response body is altered,
 * because that would be a contract change and belongs to ADR-0042.
 *
 * <p>Serialising an awkward payload never fails a request (AUD-006-f). That is NOT in tension with
 * the audit guarantee: constructing the audit RECORD is fatal (ADR-0017), emitting a log LINE about
 * a payload is not, and the two obligations are kept apart deliberately.
 */
@Component
@MigratedFrom(value = "km:node/N-0009", note = "JSON_Logger_Config; ADR-0015 supplies the masking the legacy left empty")
public class StructuredAuditEventSink implements AuditEventSink {

    private static final Logger LOG = LoggerFactory.getLogger("com.westfield.api.billing.audit");

    /** What a masked value looks like. Fixed, so a log consumer can detect redaction. */
    public static final String REDACTED = "[REDACTED]";

    private final ObjectMapper objectMapper;
    private final BillingEdgeProperties properties;

    public StructuredAuditEventSink(ObjectMapper objectMapper, BillingEdgeProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    @MigratedFrom(value = "km:node/N-0015", note = "entry/exit logging; masking applied on the way out")
    public void emit(TracePoint tracePoint, String correlationId, Map<String, Object> fields) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("tracePoint", tracePoint.name());
        entry.put("correlationId", correlationId);
        entry.putAll(redact(fields));
        LOG.info(serialise(entry));
    }

    /** The redacted view, for tests and for any caller that needs the masked shape without emitting. */
    public Map<String, Object> redact(Map<String, Object> fields) {
        Map<String, Object> redacted = new LinkedHashMap<>();
        List<String> masked = lowercase(properties.getLogging().getMaskFields());
        List<String> suppressed = lowercase(properties.getLogging().getDisableFields());
        fields.forEach((key, value) -> {
            String name = key == null ? "" : key.toLowerCase(Locale.ROOT);
            if (suppressed.contains(name)) {
                // Suppressed fields are ABSENT, not empty: that is what "disable" meant.
                return;
            }
            if (masked.contains(name)) {
                redacted.put(key, maskValue(name, value));
                return;
            }
            redacted.put(key, value instanceof Map<?, ?> nested ? redactNested(nested) : value);
        });
        return redacted;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> redactNested(Map<?, ?> nested) {
        return redact((Map<String, Object>) nested);
    }

    /**
     * The security assertion is replaced outright; identifiers go through the SAME masking rule the
     * audit trail uses, so one rule governs both streams (ADR-0015).
     */
    private static Object maskValue(String name, Object value) {
        if (value == null) {
            return null;
        }
        boolean identifier = name.contains("accountnumber") || name.contains("policynumber")
                || name.contains("agencycode");
        return identifier ? ResourcePathMasker.maskSegment(value.toString()) : REDACTED;
    }

    private String serialise(Map<String, Object> entry) {
        try {
            return objectMapper.writeValueAsString(entry);
        } catch (Exception notSerialisable) {
            // The entry is still emitted and the request is not failed (AUD-006-f). The legacy's
            // stringifyNonJSON behaviour on a malformed payload could not be determined from source
            // (N-0009 ec 2); answering with a degraded line is the choice that cannot break a caller.
            return "{\"tracePoint\":\"" + entry.get("tracePoint") + "\",\"correlationId\":\""
                    + entry.get("correlationId") + "\",\"serialisationError\":\""
                    + notSerialisable.getClass().getSimpleName() + "\"}";
        }
    }

    private static List<String> lowercase(List<String> values) {
        List<String> lowered = new ArrayList<>();
        for (String value : values) {
            if (value != null) {
                lowered.add(value.toLowerCase(Locale.ROOT));
            }
        }
        return lowered;
    }
}
