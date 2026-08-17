
package com.westfield.api.billing.platform.testing;

/**
 * A captured request/response pair used by the parity harness (ADR-0021).
 *
 * @param ref            matches a task's {@code golden_payload_ref}
 * @param origin         how it was produced; drives the evidence pack's unverified-parity count
 * @param pseudonymised  whether structure-preserving pseudonymisation was applied (R-029). The
 *                       substitution preserves length, character class, repetition and duplication,
 *                       because the shape irregularities are the evidence.
 * @param divergenceNote non-null when the expectation deliberately differs from legacy behaviour
 *                       (e.g. ADR-0024 escrow term selection, ADR-0020 204 bodies)
 */
public record GoldenPayload(
        String ref,
        BaselineOrigin origin,
        boolean pseudonymised,
        String divergenceNote) {
}
