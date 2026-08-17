
package com.westfield.api.billing.platform.spi;

/**
 * The request/response audit obligation (CAP-003, ADR-0017).
 *
 * <p>Two properties of this port are decisions, not conveniences:
 * <ul>
 *   <li>{@link #open} runs before any business logic, and a failure building the record FAILS the
 *       request. The legacy makes this a {@code flow}, not a {@code sub-flow}, for that reason.
 *       Making audit logging non-fatal would silently remove an audit guarantee.</li>
 *   <li>{@link #close} is called AFTER the final response status is known, on every path including
 *       the unhandled-failure path (ADR-0035 corrects the legacy ordering defect).</li>
 * </ul>
 */
public interface AuditRecordPort {

    /** Builds and emits the entry record. Throws to fail the request if it cannot. */
    void open(String rawUri, CallerContext caller, String interactionCorrelationId);

    /** Completes the record with the status actually returned and the elapsed wall-clock time. */
    void close(int responseStatusCode, boolean unhandledFailure);
}
