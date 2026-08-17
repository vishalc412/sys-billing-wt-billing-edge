package com.westfield.api.billing.edge.domain.security;

import com.westfield.api.billing.platform.observability.MigratedFrom;
import com.westfield.api.billing.platform.spi.CallerContext;

import java.util.List;
import java.util.Set;

/**
 * Agency entitlement on the two worklist endpoints (ADR-0037).
 *
 * <p>This is NEW BEHAVIOUR, not a port. The legacy captures the {@code agencyCodes} claim into the
 * audit log and never authorises anything with it, so {@code /pastDueToday/{agencyCode}} and
 * {@code /pendingCancelToday/{agencyCode}} serve any agency's worklist to any authenticated caller
 * (N-0035 ec 2, corroborated at N-0039 ec 2 and N-0041 ec 2). The knowledge map calls that the
 * highest-consequence unknown in the application; the data at stake is the named insured's name and
 * postal address, the policy number, the overdue amount and the cancellation date.
 *
 * <p>ADR-0037 is assumption-based: the API Manager policy set that would tell us whether a gateway
 * already enforces this was never exported (R-003). Three properties of the rule are part of the
 * decision and not implementation choices:
 * <ul>
 *   <li>an ABSENT or EMPTY claim is a DENY, not an allow — a caller with no entitlements has no
 *       agency worklist. This is the strict reading and it is the one that was chosen;</li>
 *   <li>exemptions are named, owned and dated, so cross-agency access is a list someone signed
 *       rather than a hole;</li>
 *   <li>{@code log-only} exists so that, if enforcement cannot be switched on at cutover, that is a
 *       decision a named human made rather than an engineer's default.</li>
 * </ul>
 */
@MigratedFrom(value = "km:node/N-0035",
        note = "NEW: the entitlement check the legacy never had; ADR-0037, deny on absent/empty claim")
public final class AgencyEntitlementRule {

    private final Set<String> exemptClientIds;

    public AgencyEntitlementRule(Set<String> exemptClientIds) {
        this.exemptClientIds = Set.copyOf(exemptClientIds);
    }

    /**
     * @param requestedAgencyCode the {@code {agencyCode}} path value
     * @param caller              the decoded token, may be null when no token context exists
     * @param enforcing           false selects log-only mode (ADR-0037); the request is served and
     *                            the exposure is counted instead of being closed
     */
    @MigratedFrom(value = "km:node/N-0035", note = "ADR-0037 decision table")
    public AgencyEntitlementDecision decide(String requestedAgencyCode,
                                            CallerContext caller,
                                            boolean enforcing) {
        List<String> entitlements = caller == null ? List.of() : caller.agencyCodes();
        boolean entitled = requestedAgencyCode != null && entitlements.contains(requestedAgencyCode);
        if (entitled) {
            return AgencyEntitlementDecision.ENTITLED;
        }
        String clientId = caller == null ? null : caller.clientId();
        if (clientId != null && exemptClientIds.contains(clientId)) {
            // A named service account with a recorded owner and expiry. Counted, never silent.
            return AgencyEntitlementDecision.EXEMPT;
        }
        if (!enforcing) {
            // log-only: the request is served and the cross-agency exposure is measured while it
            // persists. Reaching this branch means a human owner accepted the exposure (ADR-0037).
            return AgencyEntitlementDecision.UNENTITLED_SERVED;
        }
        // Absent or empty agencyCodes lands here too, deliberately: deny by default (ADR-0037).
        return AgencyEntitlementDecision.DENIED;
    }
}
