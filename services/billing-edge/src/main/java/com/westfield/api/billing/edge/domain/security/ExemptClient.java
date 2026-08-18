package com.westfield.api.billing.edge.domain.security;

import com.westfield.api.billing.platform.observability.MigratedFrom;

import java.time.LocalDate;

/**
 * A named, owned and dated exemption from the agency-entitlement check (ADR-0037).
 *
 * <p>An exemption is a hole someone signed for, not a permanent grant: it carries a client id (who),
 * an owner (who approved it) and an expiry (when the hole closes). The owner and expiry are
 * threaded through to the rule rather than projected away at configuration time, so the rule can
 * honour the expiry on the decision date — DEF-0108.
 *
 * <p>Expiry is boundary-inclusive: an exemption is live THROUGH its expiry date and denied the day
 * after (ADR-0037 as amended 2026-08-19). A null expiry is always live and exists only for the
 * test-seam convenience constructor of {@link AgencyEntitlementRule} that takes plain client ids.
 */
@MigratedFrom(value = "km:node/N-0035",
        note = "ADR-0037: exemption = client-id + owner + expiry; boundary-inclusive expiry")
public record ExemptClient(String clientId, String owner, LocalDate expires) {

    /**
     * @param today the decision date
     * @return true when the exemption is still live on the given date; false once the expiry has
     *         passed (strictly after — the expiry date itself is still active)
     */
    public boolean isActiveOn(LocalDate today) {
        // Live THROUGH the expiry date; denied the day after (boundary inclusive).
        return expires == null || !today.isAfter(expires);
    }
}