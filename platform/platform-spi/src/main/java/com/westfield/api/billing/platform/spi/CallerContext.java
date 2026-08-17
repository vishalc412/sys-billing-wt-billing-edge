
package com.westfield.api.billing.platform.spi;

import java.util.List;

/**
 * The decoded caller identity, as the legacy read it from the API Manager token binding
 * (km:node/N-0021, N-0051). Frozen at S3 (ADR-0001): this is the shape the audit trail (ADR-0017),
 * the impersonation rules (ADR-0036) and the agency entitlement check (ADR-0037) all agree on.
 *
 * <p>Sentinel discipline is part of the contract, not an implementation detail: an absent value is
 * the literal string {@code "EMPTY"} and an absent agency list is an empty list, so every audit line
 * has one shape. {@code clientId} is the one field the legacy leaves without that default; that
 * asymmetry is preserved and is a usable detector for "the token policy did not run".
 */
public record CallerContext(
        String subject,
        String email,
        String clientId,
        String actorSubject,
        String actorEmail,
        boolean actorFromNestedAct,
        List<String> agencyCodes) {

    public static final String EMPTY = "EMPTY";

    /** The four-character string that PingFederate uses as the "no actor" marker. Not a JSON null. */
    public static final String NULL_SENTINEL = "null";
}
