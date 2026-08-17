package com.westfield.api.billing.edge.application.failure;

import com.westfield.api.billing.platform.errors.AbsorbedFailureHandler;
import com.westfield.api.billing.platform.observability.MigratedFrom;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The one implementation of the error-parity mechanism (ADR-0010), used by all three modules.
 *
 * <p>Every backend-calling flow in the legacy is wrapped in {@code on-error-continue}, so no failure
 * ever propagates and the HTTP status is the only signal a caller gets (N-0068, N-0084). That
 * outcome is reproduced exactly for failures that originated DOWNSTREAM, and it is now counted:
 * {@code mule.parity.swallowed_error} makes visible, for the first time, how often the API answers
 * successfully over a broken back end.
 *
 * <p>Failures originating in OUR OWN code are NOT absorbed — they return 500. That is a deliberate,
 * narrow divergence: in the legacy our bug and the back end's bug are indistinguishable to a caller,
 * which would hide our own defects during cutover, when they are most likely and most correctable.
 *
 * <p>The boundary between the two is the adapter, as ADR-0010 states it. It is decided from the
 * throwing frame rather than from a marker interface because a marker would have to live in
 * {@code platform-spi} (frozen at S3) and the feature modules cannot see this module's types
 * (ADR-0001). If that classification ever needs to be explicit, it is a platform-SPI contract defect
 * for the architect, not a local workaround.
 */
@Component
@MigratedFrom(value = "km:node/N-0068",
        note = "on-error-continue parity; ADR-0010 absorbs downstream failures and counts them")
public class DefaultAbsorbedFailureHandler implements AbsorbedFailureHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAbsorbedFailureHandler.class);

    /** A frame in one of these packages means the failure came off the wire, not out of our rules. */
    private static final String ADAPTER_OUT_MARKER = ".adapter.out.";

    private final MeterRegistry meterRegistry;

    public DefaultAbsorbedFailureHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    @MigratedFrom(value = "km:node/N-0068", note = "downstream -> legacy outcome + counter; ours -> 500")
    public <T> T absorb(String capability, ThrowingSupplier<T> call, T legacyOutcome) {
        try {
            return call.get();
        } catch (Exception failure) {
            if (!originatedDownstream(failure)) {
                // Ours. Surface it; the funnel answers 500 (ADR-0010).
                throw failure instanceof RuntimeException runtime ? runtime
                        : new IllegalStateException("failure in " + capability, failure);
            }
            meterRegistry.counter("mule.parity.swallowed_error",
                    "capability", capability,
                    "error", failure.getClass().getSimpleName()).increment();
            // Logged, not returned: the caller sees exactly what the legacy gave them.
            LOG.warn("Absorbed downstream failure for {} — legacy outcome returned ({})",
                    capability, failure.getClass().getSimpleName());
            return legacyOutcome;
        }
    }

    private static boolean originatedDownstream(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            StackTraceElement[] frames = current.getStackTrace();
            if (frames.length > 0 && frames[0].getClassName().contains(ADAPTER_OUT_MARKER)) {
                return true;
            }
        }
        return false;
    }
}
