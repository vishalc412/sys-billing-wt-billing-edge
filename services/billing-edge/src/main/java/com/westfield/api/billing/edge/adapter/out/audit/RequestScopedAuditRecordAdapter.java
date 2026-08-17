package com.westfield.api.billing.edge.adapter.out.audit;

import com.westfield.api.billing.edge.application.port.AuditEventSink;
import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.edge.domain.audit.RequestResponseAuditRecord;
import com.westfield.api.billing.platform.observability.MigratedFrom;
import com.westfield.api.billing.platform.observability.TracePoint;
import com.westfield.api.billing.platform.spi.AuditRecordPort;
import com.westfield.api.billing.platform.spi.CallerContext;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * The request/response audit obligation, implemented for one request at a time (N-0050, N-0048).
 *
 * <p>{@link #open} is the target's {@code setRequestResponse}: the very first thing that happens to
 * a request, and a {@code <flow>} rather than a {@code <sub-flow>} in the legacy precisely so that a
 * failure here fails the request before any business logic runs (N-0050 ec 2). {@link #close} is
 * {@code responseLogFlow}, emitting at the END trace point with the status and the elapsed time.
 *
 * <p>State is thread-bound because a servlet request is served on one thread and because the
 * feature modules call this port through {@code platform-spi} without any request object to hand.
 * It is cleared in a finally block on {@link #close}: a leaked record would attach one request's
 * identity to the next one's audit line, which is worse than no record at all.
 *
 * <p>Calling {@link #close} with no record open FAILS. The legacy's {@code ++} merge over a null
 * {@code vars.requestResponseLog} propagates as a failure rather than producing an empty object
 * (N-0049 ec 2); substituting an empty record here would invent an audit line for a request nobody
 * ever described.
 */
@Component
@MigratedFrom(value = "km:node/N-0050", note = "setRequestResponse + responseLogFlow, one per request")
public class RequestScopedAuditRecordAdapter implements AuditRecordPort {

    private static final ThreadLocal<RequestResponseAuditRecord> CURRENT = new ThreadLocal<>();

    private final AuditEventSink sink;
    private final BillingEdgeProperties properties;
    private final Clock clock;

    public RequestScopedAuditRecordAdapter(AuditEventSink sink, BillingEdgeProperties properties, Clock clock) {
        this.sink = sink;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @MigratedFrom(value = "km:node/N-0051", note = "requestResponseLog construction at request entry")
    public void open(String rawUri, CallerContext caller, String interactionCorrelationId) {
        // now() and currentMilliseconds() are read at slightly different points inside the legacy
        // expression, so the two are independent readings of the same instant and may differ by a
        // small interval (N-0051 ec 5). Reproduced rather than derived from one another.
        OffsetDateTime entryTimestamp = OffsetDateTime.now(clock);
        long entryMillis = clock.millis();
        RequestResponseAuditRecord record = new RequestResponseAuditRecord(
                entryTimestamp,
                entryMillis,
                properties.getApi().getName(),
                properties.getApi().getVersion(),
                MDC.get("correlationId"),
                interactionCorrelationId,
                rawUri,
                caller);
        CURRENT.set(record);
        try {
            sink.emit(TracePoint.START, record.correlationId(), record.entryFields());
        } catch (RuntimeException entryEmissionFailed) {
            // The request is about to be failed (N-0050 ec 2). Unbind first: servlet threads are
            // pooled, and a record left bound here would attach this request's identity to the next
            // request served on the same thread — a worse outcome than the failure itself.
            CURRENT.remove();
            throw entryEmissionFailed;
        }
    }

    @Override
    @MigratedFrom(value = "km:node/N-0049", note = "completion with status and elapsed time; ADR-0035 ordering")
    public void close(int responseStatusCode, boolean unhandledFailure) {
        RequestResponseAuditRecord record = CURRENT.get();
        if (record == null) {
            throw new IllegalStateException(
                    "No audit record was opened for this request. The legacy merge over a null "
                            + "record propagates as a failure (N-0049 ec 2); an empty record is not "
                            + "substituted, because that would invent an audit line.");
        }
        try {
            sink.emit(TracePoint.END, record.correlationId(),
                    record.completedFields(responseStatusCode, clock.millis(), unhandledFailure));
        } finally {
            CURRENT.remove();
        }
    }

    /** The record currently open, for the components that project from it. */
    public static RequestResponseAuditRecord current() {
        return CURRENT.get();
    }

    /** Test and error-path hygiene: never leave a record bound to a pooled thread. */
    public static void clear() {
        CURRENT.remove();
    }
}
