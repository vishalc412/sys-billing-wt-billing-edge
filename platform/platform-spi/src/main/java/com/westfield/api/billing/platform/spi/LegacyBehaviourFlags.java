
package com.westfield.api.billing.platform.spi;

/**
 * Named switches for behaviours that an ADR decided deliberately and that a future maintainer will
 * otherwise "fix". Every flag here points at the ADR that owns it. Adding a flag without an ADR is a
 * contract defect routed to the architect.
 */
public interface LegacyBehaviourFlags {

    /** ADR-0037. Default: enforce. log-only carries a known cross-agency PII exposure. */
    boolean enforceAgencyEntitlement();

    /** ADR-0038. Default: false in production. */
    boolean consoleEnabled();

    /** ADR-0007. Zero means mint per request, which is the cutover default. */
    long samlCacheTtlSeconds();
}
