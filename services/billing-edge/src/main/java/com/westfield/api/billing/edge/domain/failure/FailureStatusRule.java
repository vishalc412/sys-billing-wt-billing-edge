package com.westfield.api.billing.edge.domain.failure;

import com.westfield.api.billing.platform.observability.MigratedFrom;

/**
 * The status a failed request is answered with (N-0070).
 *
 * <p>The back end's status is relayed VERBATIM. A 404 from the ESB becomes a 404 from this API even
 * though the API resource exists, and a backend 401 or 403 becomes a caller-facing authentication
 * error even though the caller's own credentials were fine. That is a recorded legacy defect
 * (N-0068 defect) and ADR-0033 PRESERVES it, because no consumer inventory exists (R-019) and
 * remapping would change the status codes consumers see today. What ADR-0033 adds instead is the
 * additive {@code X-Billing-Failure-Origin} header and counters, so the misleading relay becomes
 * measurable before anyone argues about changing it.
 *
 * <p>Ordering is load-bearing and is preserved as an explicit two step: the body is built FIRST and
 * the status derived SECOND from the error attributes, which survive the payload replacement
 * (N-0068 ec 2). Reordering silently breaks the derivation.
 */
@MigratedFrom(value = "km:node/N-0070", note = "httpStatus derivation; ADR-0033 preserves the verbatim relay")
public final class FailureStatusRule {

    /** The status when the failure was not an HTTP failure at all (N-0070). */
    public static final int NO_UPSTREAM_STATUS = 500;

    private FailureStatusRule() {
    }

    /**
     * @param upstreamStatus the status the back end gave, as it arrives — possibly null, possibly a
     *                       non-numeric value
     * @return the status to return to the caller
     */
    @MigratedFrom(value = "km:node/N-0070", note = "'as Number default 500'; the default fires only on null")
    public static int statusFor(Object upstreamStatus) {
        if (upstreamStatus == null) {
            // Mapping failure, refused connection, anything with no HTTP status: 500.
            return NO_UPSTREAM_STATUS;
        }
        if (upstreamStatus instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(upstreamStatus.toString().trim());
        } catch (NumberFormatException notNumeric) {
            // Narrow, deliberate correction (ERR-002-e). The legacy 'as Number' would RAISE here,
            // inside an error handler that has no further handler, turning a backend oddity into an
            // unhandled failure. Answering 500 is the outcome the legacy was reaching for.
            return NO_UPSTREAM_STATUS;
        }
    }

    /** True for the statuses whose verbatim relay is misleading enough to be counted (ADR-0033). */
    public static boolean isRelayedAuthStatus(int status) {
        return status == 401 || status == 403;
    }
}
