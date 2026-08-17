
package com.westfield.api.billing.platform.testing;

/**
 * How a parity baseline was produced (ADR-0021). This is not metadata for tidiness: a suite that is
 * green against SYNTHETIC-only baselines for an endpoint is reported as UNVERIFIED PARITY, not as
 * parity. Four of eight endpoints have no legacy coverage at all (R-020) and there may be no runnable
 * legacy instance (R-025); labelling is what stops that degrading into a green board over a broken
 * system.
 */
public enum BaselineOrigin {
    /** Captured from a running legacy instance (dev/test, never local). Strongest evidence. */
    CAPTURED_LEGACY,
    /** Constructed from knowledge-map edge cases. Encodes our understanding, not the back end's. */
    SYNTHETIC,
    /** Read-only shape census from production. Requires R-029 approval; shapes only, never payloads. */
    PRODUCTION_SHAPE_CENSUS
}
