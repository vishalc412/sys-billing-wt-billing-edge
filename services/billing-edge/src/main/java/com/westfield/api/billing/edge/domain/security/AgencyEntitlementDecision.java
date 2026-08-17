package com.westfield.api.billing.edge.domain.security;

import com.westfield.api.billing.platform.observability.MigratedFrom;

/** The four outcomes of {@link AgencyEntitlementRule}. Three of them serve the request. */
@MigratedFrom(value = "km:node/N-0035", note = "ADR-0037 outcomes; each one is counted separately")
public enum AgencyEntitlementDecision {

    /** The claim contains the requested code. */
    ENTITLED(true),
    /** Not entitled, but the client id is on the named exemption list. Counted. */
    EXEMPT(true),
    /** Not entitled; enforcement is in log-only mode. Served, and counted as an exposure. */
    UNENTITLED_SERVED(true),
    /** Not entitled, enforcement on. 403, and no backend call is made. */
    DENIED(false);

    private final boolean served;

    AgencyEntitlementDecision(boolean served) {
        this.served = served;
    }

    public boolean served() {
        return served;
    }
}
