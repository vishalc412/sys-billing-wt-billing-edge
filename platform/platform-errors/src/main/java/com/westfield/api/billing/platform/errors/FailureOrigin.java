
package com.westfield.api.billing.platform.errors;

/**
 * Which system actually failed. Emitted on the {@code X-Billing-Failure-Origin} response header for
 * every non-2xx produced by the failure path (ADR-0033).
 *
 * <p>The legacy relays the back end's HTTP status verbatim, so a caller receiving 401 concludes its
 * own credentials were rejected when in fact our service account failed. ADR-0033 preserves the
 * status and adds this header rather than remapping, because no consumer inventory exists (R-019).
 */
public enum FailureOrigin {
    UPSTREAM_ESB,
    UPSTREAM_WAS,
    UPSTREAM_STS,
    UPSTREAM_VAULT,
    GATEWAY,
    THIS_SERVICE
}
