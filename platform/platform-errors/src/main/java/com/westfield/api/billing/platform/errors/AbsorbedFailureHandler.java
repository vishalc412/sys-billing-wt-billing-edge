
package com.westfield.api.billing.platform.errors;

/**
 * The error-parity mechanism (ADR-0010). Implemented once in {@code billing-edge}; used by every
 * module. Services do not reimplement it.
 *
 * <p>Every backend-calling flow in the legacy uses {@code on-error-continue}, so NO failure ever
 * propagates and the HTTP status is the only signal. This handler reproduces that outcome for
 * failures that originated DOWNSTREAM, and additionally emits {@code mule.parity.swallowed_error}
 * tagged with the capability and error class so the path is visible for the first time.
 *
 * <p>Failures originating in OUR OWN code (mapping, projection, normalisation) are NOT absorbed:
 * they return 500. That is a deliberate, narrow divergence — in the legacy, our bug and the back
 * end's bug are indistinguishable to the caller, which would hide our defects during cutover.
 * The boundary is the adapter: thrown while reading the wire is downstream; thrown in domain or
 * application is ours.
 */
public interface AbsorbedFailureHandler {

    /** @param capability e.g. "CAP-006"; used as a metric tag, not for control flow. */
    <T> T absorb(String capability, ThrowingSupplier<T> call, T legacyOutcome);

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
