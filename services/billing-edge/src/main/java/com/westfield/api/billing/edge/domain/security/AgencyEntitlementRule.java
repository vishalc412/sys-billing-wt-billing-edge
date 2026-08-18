package com.westfield.api.billing.edge.domain.security;

import com.westfield.api.billing.platform.observability.MigratedFrom;
import com.westfield.api.billing.platform.spi.CallerContext;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 *       rather than a hole; an exemption is EXEMPT only while it is live on the decision date
 *       (DEF-0108: the owner and expiry are threaded through here, not projected away);</li>
 *   <li>{@code log-only} exists so that, if enforcement cannot be switched on at cutover, that is a
 *       decision a named human made rather than an engineer's default.</li>
 * </ul>
 *
 * <p>Comparison rule (DEF-0106, ADR-0037 as amended 2026-08-19): the legacy captured
 * {@code agencyCodes} for audit only and never compared it, so the comparison rule is a NEW
 * decision, not heritage. Both sides are normalised — trimmed of surrounding whitespace and
 * upper-cased — before an exact-match membership test. A caller whose claim differs only in case
 * or padding is the same caller the legacy served; a malformed (non-list) claim is fail-closed
 * (deny), unchanged.
 */
@MigratedFrom(value = "km:node/N-0035",
        note = "NEW: the entitlement check the legacy never had; ADR-0037, deny on absent/empty claim")
public final class AgencyEntitlementRule {

    private final Map<String, ExemptClient> exemptClients;
    private final Clock clock;

    /**
     * Convenience constructor for tests that only need client ids. The exemptions have no expiry
     * (always live) and the decision date is the system clock. Production wires the full list +
     * clock via {@link #AgencyEntitlementRule(List, Clock)}.
     */
    public AgencyEntitlementRule(Set<String> exemptClientIds) {
        this(toExemptClients(exemptClientIds), Clock.systemUTC());
    }

    /**
     * @param exemptClients the configured exemptions with owner + expiry (ADR-0037)
     * @param clock          the source of the decision date used to evaluate exemption expiry
     */
    public AgencyEntitlementRule(List<ExemptClient> exemptClients, Clock clock) {
        Map<String, ExemptClient> map = new LinkedHashMap<>();
        for (ExemptClient exemption : exemptClients) {
            if (exemption != null && exemption.clientId() != null) {
                map.putIfAbsent(exemption.clientId(), exemption);
            }
        }
        this.exemptClients = Map.copyOf(map);
        this.clock = clock;
    }

    private static List<ExemptClient> toExemptClients(Set<String> exemptClientIds) {
        return exemptClientIds.stream()
                .filter(id -> id != null)
                .map(id -> new ExemptClient(id, null, null))
                .toList();
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
        // DEF-0106 (ADR-0037): normalise both sides — trim + uppercase — before exact-match.
        String normalizedRequest = normalize(requestedAgencyCode);
        boolean entitled = normalizedRequest != null && entitlements.stream()
                .map(AgencyEntitlementRule::normalize)
                .anyMatch(normalizedRequest::equals);
        if (entitled) {
            return AgencyEntitlementDecision.ENTITLED;
        }
        // DEF-0108: an exemption is EXEMPT only while it is live on the decision date. The owner and
        // expiry are threaded through here, so a past-expiry exemption denies instead of granting
        // cross-agency PII access forever.
        String clientId = caller == null ? null : caller.clientId();
        ExemptClient exemption = clientId == null ? null : exemptClients.get(clientId);
        if (exemption != null && exemption.isActiveOn(LocalDate.now(clock))) {
            // A named service account with a recorded owner and a live expiry. Counted, never silent.
            return AgencyEntitlementDecision.EXEMPT;
        }
        if (!enforcing) {
            // log-only: the request is served and the cross-agency exposure is measured while it
            // persists. Reaching this branch means a human owner accepted the exposure (ADR-0037).
            return AgencyEntitlementDecision.UNENTITLED_SERVED;
        }
        // Absent or empty agencyCodes lands here too, deliberately: deny by default (ADR-0037).
        // A malformed (non-list) claim is converted to an empty list upstream and denies the same way.
        return AgencyEntitlementDecision.DENIED;
    }

    /** DEF-0106: trim surrounding whitespace and upper-case before exact comparison. */
    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}