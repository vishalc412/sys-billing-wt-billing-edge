package com.westfield.api.billing.edge.testsupport;

import com.westfield.api.billing.edge.application.port.AuditEventSink;
import com.westfield.api.billing.platform.observability.TracePoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An {@link AuditEventSink} that keeps what it was given, so the audit stream can be asserted as the
 * contract ADR-0017 says it is rather than by scraping a log appender.
 */
public class RecordingAuditEventSink implements AuditEventSink {

    private final List<Emission> emissions = new ArrayList<>();
    private RuntimeException failWith;
    private TracePoint failAt;

    /** Make every emission fail, to exercise the audit-construction-fails-the-request semantic. */
    public void failWith(RuntimeException failure) {
        this.failWith = failure;
        this.failAt = null;
    }

    /**
     * Make only the emission at one trace point fail. Entry failure and exit failure are DIFFERENT
     * obligations in the legacy — one aborts before business logic, the other surfaces after it —
     * so the two must be triggerable independently.
     */
    public void failAt(TracePoint tracePoint, RuntimeException failure) {
        this.failWith = failure;
        this.failAt = tracePoint;
    }

    @Override
    public void emit(TracePoint tracePoint, String correlationId, Map<String, Object> fields) {
        if (failWith != null && (failAt == null || failAt == tracePoint)) {
            throw failWith;
        }
        emissions.add(new Emission(tracePoint, correlationId, new LinkedHashMap<>(fields)));
    }

    public List<Emission> emissions() {
        return List.copyOf(emissions);
    }

    public List<Emission> at(TracePoint tracePoint) {
        return emissions.stream().filter(e -> e.tracePoint() == tracePoint).toList();
    }

    public Emission only(TracePoint tracePoint) {
        List<Emission> matching = at(tracePoint);
        if (matching.size() != 1) {
            throw new AssertionError("expected exactly one " + tracePoint + " emission, found " + matching.size());
        }
        return matching.get(0);
    }

    public void clear() {
        emissions.clear();
        failWith = null;
        failAt = null;
    }

    public record Emission(TracePoint tracePoint, String correlationId, Map<String, Object> fields) {
    }
}
