package com.westfield.api.billing.edge.application.port;

import com.westfield.api.billing.platform.observability.MigratedFrom;
import com.westfield.api.billing.platform.observability.TracePoint;

import java.util.Map;

/**
 * Where an audit record goes (N-0009, N-0015, N-0048).
 *
 * <p>A port rather than a direct logger call for one reason: the audit record is a domain object
 * whose field set is a contract (ADR-0017), and the domain must stay testable without a logging
 * framework. The masking and field-suppression settings live behind this port too, so one
 * implementation governs both the audit stream and the payload log (ADR-0015).
 */
@MigratedFrom(value = "km:node/N-0009", note = "JSON logger; trace-point taxonomy is the log contract")
public interface AuditEventSink {

    /**
     * Emits one structured entry.
     *
     * @throws RuntimeException only when the record itself cannot be constructed. Serialisation of
     *         an awkward PAYLOAD never fails a request (AUD-006-f); a failure to record the AUDIT
     *         line does (ADR-0017). The two are different obligations and are kept apart on purpose.
     */
    void emit(TracePoint tracePoint, String correlationId, Map<String, Object> fields);
}
