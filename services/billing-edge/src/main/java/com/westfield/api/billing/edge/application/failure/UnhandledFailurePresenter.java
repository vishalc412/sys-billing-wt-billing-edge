package com.westfield.api.billing.edge.application.failure;

import com.westfield.api.billing.edge.domain.failure.AbsorbedFailureBody;
import com.westfield.api.billing.edge.domain.failure.FailureStatusRule;
import com.westfield.api.billing.platform.errors.FailureOrigin;
import com.westfield.api.billing.platform.observability.MigratedFrom;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * The organisation-wide error presentation — the target's stand-in for {@code sapi-common-errorhandler}
 * (N-0012, N-0016, N-0030).
 *
 * <p><b>This class implements an ASSUMED contract.</b> {@code westfield-common-errorhandler.xml} is
 * packaged inside an Exchange module that is not in the source tree, and the knowledge map calls it
 * "the single largest behavioural blind spot in the application": what it does to the payload, what
 * status it sets, and whether it maps backend faults to the 597/598/599 codes the RAML declares are
 * all unknown (R-013). ADR-0016 decides to implement it behind an interface with the assumption
 * stated in code and in the contract under {@code x-unknown: R-013}, rather than to block six
 * capabilities on an email.
 *
 * <p>The assumption is exactly three things, and no more:
 * <ol>
 *   <li>the body is the {@code error_Flow} shape (N-0069);</li>
 *   <li>the status is the derived status of ADR-0033 — the back end's status relayed verbatim,
 *       500 when there was no HTTP failure;</li>
 *   <li><b>no</b> 597/598/599 mapping is invented. If the shared handler emits those for timeouts
 *       they are live behaviour a consumer may branch on, and guessing at them would be worse than
 *       not emitting them (ADR-0042).</li>
 * </ol>
 *
 * <p>{@link #ASSUMED_CONTRACT} is asserted by a test on purpose. ERR-003-h says this task is
 * REPORTED AS BLOCKED rather than quietly implemented against a guess; the flag and the defect
 * record are the mechanical form of that report.
 */
@Component
@MigratedFrom(value = "km:node/N-0016",
        note = "ASSUMED contract for the opaque sapi-common-errorhandler; ADR-0016, risk R-013")
public class UnhandledFailurePresenter {

    /** True while the module owner has not supplied the real contract. Asserted by ERR-003-h. */
    public static final boolean ASSUMED_CONTRACT = true;

    /** The open risk this assumption is tracked under. */
    public static final String ASSUMPTION_RISK = "R-013";

    /** The timeout statuses the legacy RAML declares. Deliberately NOT emitted (ADR-0042). */
    public static final int[] UNMAPPED_DECLARED_TIMEOUT_STATUSES = {597, 598, 599};

    /**
     * Shapes a failure that no endpoint absorbed.
     *
     * @param upstreamStatus the back end's status when there was one, else null
     */
    @MigratedFrom(value = "km:node/N-0030", note = "catch-all: body then status, per N-0068 ec 2")
    public Presentation present(Throwable failure, String correlationId, Object upstreamStatus) {
        // Payload FIRST, status SECOND, from attributes the payload replacement does not disturb
        // (N-0068 ec 2). The two steps are ordered explicitly because reordering them silently
        // breaks the status derivation.
        AbsorbedFailureBody body = new AbsorbedFailureBody(
                AbsorbedFailureBody.FAULT_ACTOR,
                failure == null ? null : failure.getMessage(),
                errorTypeOf(failure),
                causeOf(failure),
                correlationId);
        int status = FailureStatusRule.statusFor(upstreamStatus);
        FailureOrigin origin = upstreamStatus == null ? FailureOrigin.THIS_SERVICE : FailureOrigin.UPSTREAM_ESB;
        return new Presentation(status, body.asMap(), origin);
    }

    private static AbsorbedFailureBody.ErrorType errorTypeOf(Throwable failure) {
        if (failure == null) {
            return null;
        }
        // Namespace/identifier, mirroring the Mule error type's two-part serialisation (N-0069 ec 3).
        return new AbsorbedFailureBody.ErrorType("MULE", failure.getClass().getSimpleName());
    }

    private static String causeOf(Throwable failure) {
        Throwable cause = failure == null ? null : failure.getCause();
        // Null-valued key, never an omitted key (N-0069 ec 4). Unredacted in the RESPONSE by
        // decision: ADR-0015 masks the log and explicitly leaves the body alone.
        return cause == null ? null : cause.toString();
    }

    /** Status, body and the additive failure-origin header value of ADR-0033. */
    public record Presentation(int status, Map<String, Object> body, FailureOrigin origin) {
    }
}
