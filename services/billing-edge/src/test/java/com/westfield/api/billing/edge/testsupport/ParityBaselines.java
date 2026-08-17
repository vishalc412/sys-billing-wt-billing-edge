package com.westfield.api.billing.edge.testsupport;

import com.westfield.api.billing.platform.testing.BaselineOrigin;
import com.westfield.api.billing.platform.testing.GoldenPayload;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The register of every {@code golden_payload_ref} this work packet names, and what evidence — if
 * any — actually stands behind it.
 *
 * <p><b>This class exists so that "blocked" is a fact the build asserts, not a sentence in a report
 * nobody reads.</b> WP-001 declares twenty-odd parity criteria. Not one of them can be satisfied
 * today: R-025 records that there may be no runnable legacy instance, and R-029 records that no
 * data-handling treatment has been agreed for the customer PII these payloads carry, so no capture
 * may lawfully be taken even if an instance existed. PAR-001 and PAR-002 are the tasks that would
 * produce the evidence and both are blocked on exactly those two risks.
 *
 * <p>ADR-0021 is explicit that a suite green against no baseline, or against synthetic baselines
 * only, is reported as UNVERIFIED PARITY and never as parity. {@code ParityEvidenceTest} asserts
 * that every ref below is declared and correctly labelled; the moment a real capture lands, the entry
 * changes to {@link BaselineOrigin#CAPTURED_LEGACY} and the corresponding comparison becomes real.
 *
 * <p>The alternative — writing a parity test that compares the migrated output against an expectation
 * written by the same person who wrote the migration — is the vacuous suite PAR-001-g prohibits by
 * name. It compares a mock to itself and reports it as evidence.
 */
public final class ParityBaselines {

    /** Why no baseline exists, in the words the risk register uses. */
    public static final String NO_LEGACY_INSTANCE = "R-025: no runnable legacy instance is available to capture from";
    public static final String NO_DATA_TREATMENT = "R-029: no agreed data-handling treatment for the PII in these payloads";
    public static final String BOTH = NO_LEGACY_INSTANCE + "; " + NO_DATA_TREATMENT;

    private static final Map<String, Entry> REGISTER = new LinkedHashMap<>();

    static {
        // ADM — routing, status and content-type behaviour.
        blocked("out/golden/ADM-001/stale-status-on-failure", "ADM-001-e", NO_LEGACY_INSTANCE);
        blocked("out/golden/ADM-001/content-type-census", "ADM-001-f", NO_LEGACY_INSTANCE);
        blocked("out/golden/ADM-004/audit-record-per-callsite", "ADM-004-g", BOTH);

        // AUD — the audit stream.
        blocked("out/golden/AUD-001/entry-audit-record", "AUD-001-j", BOTH);
        blocked("out/golden/AUD-004/missing-entry-record", "AUD-004-c", NO_LEGACY_INSTANCE);
        blocked("out/golden/AUD-006/logging-step-failure", "AUD-006-h", NO_LEGACY_INSTANCE);

        // ERR — failure bodies and statuses.
        blocked("out/golden/ERR-001/backend-failure-bodies", "ERR-001-h", BOTH);
        blocked("out/golden/ERR-003/unhandled-failure-responses", "ERR-003-g", NO_LEGACY_INSTANCE);

        // INF, SEC, TOK.
        blocked("out/golden/INF-001/info-response", "INF-001-f", NO_LEGACY_INSTANCE);
        blocked("out/golden/SEC-001/token-rejection", "SEC-001-f", "R-003: the API Manager policy set was never exported, so the rejection body is unknown");
        blocked("out/golden/SEC-002/entitled-caller-worklist", "SEC-002-c", BOTH);
        blocked("out/golden/TOK-002/malformed-sts-response-was", "TOK-002-d", "R-006: the STS wire protocol is internal to an unavailable Exchange module");
        blocked("out/golden/TOK-002/malformed-sts-response-esb", "TOK-002-e", "R-006: the STS wire protocol is internal to an unavailable Exchange module");

        // PAR-001 — the capture task for the endpoints with no legacy coverage.
        blocked("out/golden/PAR-001/external-account-view", "PAR-001-a", BOTH);
        blocked("out/golden/PAR-001/agency-worklists", "PAR-001-b", BOTH);
        blocked("out/golden/PAR-001/absorbed-failures", "PAR-001-c", NO_LEGACY_INSTANCE);
        blocked("out/golden/PAR-001/204-body-census", "PAR-001-d", NO_LEGACY_INSTANCE);
        blocked("out/golden/PAR-001/bcms-account-summary", "PAR-001-e", BOTH);

        // PAR-002 — the capture task for the audit record and the contract violations.
        blocked("out/golden/PAR-002/audit-record-success", "PAR-002-a", BOTH);
        blocked("out/golden/PAR-002/impersonation-cases", "PAR-002-b", BOTH);
        blocked("out/golden/PAR-002/masked-paths", "PAR-002-c", BOTH);
        blocked("out/golden/PAR-002/contract-violations", "PAR-002-d", NO_LEGACY_INSTANCE);
        blocked("out/golden/PAR-002/unhandled-failures", "PAR-002-e", NO_LEGACY_INSTANCE);
        blocked("out/golden/PAR-002/stale-status", "PAR-002-f", NO_LEGACY_INSTANCE);
        blocked("out/golden/PAR-002/audit-record-completion", "PAR-002-g", NO_LEGACY_INSTANCE);
        blocked("out/golden/PAR-002/shared-logging-side-effects", "PAR-002-h", NO_LEGACY_INSTANCE);
    }

    private ParityBaselines() {
    }

    private static void blocked(String ref, String criterionId, String reason) {
        REGISTER.put(ref, new Entry(
                new GoldenPayload(ref, BaselineOrigin.SYNTHETIC, false,
                        "NO BASELINE CAPTURED. " + reason),
                criterionId,
                false,
                reason));
    }

    public static Optional<Entry> find(String ref) {
        return Optional.ofNullable(REGISTER.get(ref));
    }

    public static Map<String, Entry> all() {
        return Map.copyOf(REGISTER);
    }

    /** True only when a capture from a real legacy instance stands behind the ref. */
    public static boolean isVerifiedParity(String ref) {
        return find(ref).map(Entry::captured).orElse(false);
    }

    /**
     * @param captured whether evidence exists at all. False for every entry today; flipping one to
     *                 true without also supplying the payload is what {@code ParityEvidenceTest}
     *                 catches.
     */
    public record Entry(GoldenPayload payload, String criterionId, boolean captured, String blockedReason) {
    }
}
