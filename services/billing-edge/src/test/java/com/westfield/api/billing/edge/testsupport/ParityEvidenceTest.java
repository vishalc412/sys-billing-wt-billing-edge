package com.westfield.api.billing.edge.testsupport;

import com.westfield.api.billing.platform.testing.BaselineOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PAR-001 and PAR-002, and every parity criterion that depends on them.
 *
 * <p><b>What this test asserts is that the evidence does not exist.</b> That is not a placeholder and
 * it is not a skipped test — it is the finding, asserted mechanically so that it cannot be forgotten
 * and cannot quietly become "green" without someone supplying a capture.
 *
 * <p>Every criterion below is {@code verifiable_by: parity}, meaning it can only be discharged by
 * comparing the migrated output against a recording of what the legacy actually did. WP-001 records
 * two risks that make that impossible today:
 * <ul>
 *   <li><b>R-025</b> — there may be no runnable legacy instance to capture from, and the local
 *       configuration cannot even start three of the eight endpoints (N-0003 ec 2, N-0101 ec 1);</li>
 *   <li><b>R-029</b> — no data-handling treatment has been agreed for the customer PII these payloads
 *       carry (the named insured's name and postal address, policy numbers, overdue amounts), so no
 *       capture may lawfully be taken even if an instance existed.</li>
 * </ul>
 *
 * <p>ADR-0021 is explicit about what may and may not be done in that situation: a suite green against
 * no baseline is reported as UNVERIFIED PARITY and never as parity. The prohibited alternative has a
 * name in this packet — PAR-001-g calls it "the vacuous suite" and excludes it: a test that compares
 * the migrated output against an expectation written by the same person who wrote the migration
 * compares a mock to itself and reports the result as evidence.
 *
 * <p>So this class asserts three things and nothing more:
 * <ol>
 *   <li>every {@code golden_payload_ref} named anywhere in WP-001 is declared in the register;</li>
 *   <li>each is labelled with its true origin and the risk that blocks it;</li>
 *   <li>none is labelled {@link BaselineOrigin#CAPTURED_LEGACY}, because none is.</li>
 * </ol>
 * When a real capture lands, its register entry flips to captured and the corresponding comparison
 * becomes a real assertion. Until then the build states the truth out loud.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("PAR-001 / PAR-002 parity evidence register")
class ParityEvidenceTest {

    // ---------------------------------------------------------------------------------------------
    // PAR-001 — baseline capture for the endpoints with no legacy test coverage.
    // ---------------------------------------------------------------------------------------------

    @Test // PAR-001-a
    @DisplayName("a: the external billing view baseline (by account and by policy) has not been captured")
    void externalAccountViewBaselineIsBlocked() {
        assertBlocked("out/golden/PAR-001/external-account-view", "PAR-001-a");
    }

    @Test // PAR-001-b
    @DisplayName("b: the agency worklist baselines (populated, single, empty element, no element) are blocked")
    void agencyWorklistBaselineIsBlocked() {
        assertBlocked("out/golden/PAR-001/agency-worklists", "PAR-001-b");
    }

    @Test // PAR-001-c
    @DisplayName("c: the absorbed-failure baselines for the five failure-absorbing endpoints are blocked")
    void absorbedFailureBaselineIsBlocked() {
        assertBlocked("out/golden/PAR-001/absorbed-failures", "PAR-001-c");
    }

    @Test // PAR-001-d
    @DisplayName("d: the 204-body census that would close R-009 has not been taken")
    void twoZeroFourBodyCensusIsBlocked() {
        // R-009: nobody knows whether a legacy 204 carried a body. ADR-0031 and ADR-0020 both had to
        // be decided without it. The census is the only thing that answers it, and it needs a
        // running instance.
        assertBlocked("out/golden/PAR-001/204-body-census", "PAR-001-d");
    }

    @Test // PAR-001-e
    @DisplayName("e: the BCMS account-summary baseline, including the repeating policy term, is blocked")
    void bcmsAccountSummaryBaselineIsBlocked() {
        assertBlocked("out/golden/PAR-001/bcms-account-summary", "PAR-001-e");
    }

    @Test // PAR-001-f
    @DisplayName("f: every register entry says what it is evidence of, or that it is evidence of nothing")
    void everyBaselineCarriesItsProvenance() {
        // The criterion asks that a baseline record the endpoint, the input condition, the capture
        // date and the source environment. None of those exist for an uncaptured baseline, so what
        // is asserted here is the honest equivalent: every entry names the criterion it serves and
        // states, in the words of the risk register, why no capture stands behind it. A blank or
        // vague reason is exactly how a blocked item turns into an assumed-fine item six weeks later.
        for (Map.Entry<String, ParityBaselines.Entry> entry : ParityBaselines.all().entrySet()) {
            ParityBaselines.Entry value = entry.getValue();
            assertThat(value.criterionId())
                    .as("%s must name the criterion it serves", entry.getKey())
                    .matches("[A-Z]{3}-\\d{3}-[a-j]");
            assertThat(value.blockedReason())
                    .as("%s must name the risk that blocks it", entry.getKey())
                    .isNotBlank()
                    .matches("(?s).*R-0\\d\\d.*");
            assertThat(value.payload().divergenceNote())
                    .as("%s must say plainly that no baseline was captured", entry.getKey())
                    .startsWith("NO BASELINE CAPTURED.");
            assertThat(value.payload().ref()).isEqualTo(entry.getKey());
        }
    }

    @Test // PAR-001-g
    @DisplayName("g: the vacuous suite and the stale fixtures are excluded, and the exclusion is recorded")
    void vacuousAndStaleLegacyFixturesAreExcluded() {
        // A prohibition, not a preference. The legacy MUnit suite stubs the back end and then asserts
        // the stub's own response, so it passes whatever the flow does; the two other fixture sets
        // document a contract the API no longer serves. Carrying any of them forward would produce a
        // green parity signal backed by nothing, which is worse than no signal at all.
        assertThat(EXCLUDED_LEGACY_FIXTURES)
                .as("each exclusion must carry its reason")
                .allSatisfy(exclusion -> assertThat(exclusion.reason()).isNotBlank());
        assertThat(EXCLUDED_LEGACY_FIXTURES).hasSize(3);
        assertThat(EXCLUDED_LEGACY_FIXTURES)
                .extracting(ExcludedFixture::name)
                .contains("legacy MUnit suite (compares the mock to itself)");

        // And the exclusion must be total: no register entry may cite an excluded fixture as its
        // evidence, which is the mechanical form of the prohibition.
        for (ParityBaselines.Entry entry : ParityBaselines.all().values()) {
            for (ExcludedFixture excluded : EXCLUDED_LEGACY_FIXTURES) {
                assertThat(entry.blockedReason()).doesNotContain(excluded.name());
            }
        }
    }

    @Test // PAR-001-h
    @DisplayName("h: no captured payload containing customer data exists, so none has bypassed the data treatment")
    void noCustomerDataHasBeenCapturedWithoutTreatment() {
        // The criterion governs captured payloads. There are none, and R-029 is precisely why: the
        // treatment has not been agreed. Asserting emptiness is what keeps that true — the moment
        // someone adds a capture, `pseudonymised` must be true or this fails.
        for (Map.Entry<String, ParityBaselines.Entry> entry : ParityBaselines.all().entrySet()) {
            if (entry.getValue().captured()) {
                assertThat(entry.getValue().payload().pseudonymised())
                        .as("%s carries customer data and must have been through the agreed treatment "
                                + "(R-029) before it may be stored", entry.getKey())
                        .isTrue();
            }
        }
        assertThat(ParityBaselines.all().values())
                .as("no capture may be stored until R-029 agrees a data-handling treatment")
                .noneMatch(ParityBaselines.Entry::captured);
    }

    // ---------------------------------------------------------------------------------------------
    // PAR-002 — baseline capture for the audit record and the contract-violation responses.
    // ---------------------------------------------------------------------------------------------

    @Test // PAR-002-a
    @DisplayName("a: the per-endpoint successful audit-record baseline is blocked")
    void auditRecordSuccessBaselineIsBlocked() {
        assertBlocked("out/golden/PAR-002/audit-record-success", "PAR-002-a");
    }

    @Test // PAR-002-b
    @DisplayName("b: the four impersonation-encoding baselines are blocked")
    void impersonationCaseBaselinesAreBlocked() {
        // Nested act, flat actSub, flat actSub carrying the literal text "null", and no actor at all.
        // AUD-003 implements all four from the prose rule (ADR-0036 ships both definitions); what is
        // missing is the recording that proves the migrated output matches.
        assertBlocked("out/golden/PAR-002/impersonation-cases", "PAR-002-b");
    }

    @Test // PAR-002-c
    @DisplayName("c: the masked-path baselines are blocked, so AUD-002 is specification and not parity")
    void maskedPathBaselinesAreBlocked() {
        // AUD-002's notes say this outright: the masking rule has NO legacy test coverage at all, so
        // criteria a-f are a NEW specification of existing behaviour. They are asserted as unit tests
        // in ResourcePathMaskerTest, and they are not parity evidence until this capture exists.
        assertBlocked("out/golden/PAR-002/masked-paths", "PAR-002-c");
    }

    @Test // PAR-002-d
    @DisplayName("d: no baseline exists for any of the six contract-violation classes")
    void contractViolationBaselinesAreBlocked() {
        assertBlocked("out/golden/PAR-002/contract-violations", "PAR-002-d");
    }

    @Test // PAR-002-e
    @DisplayName("e: the unhandled-failure baseline is blocked, which is why ERR-003 is an assumed contract")
    void unhandledFailureBaselineIsBlocked() {
        // R-013: nobody has seen what sapi-common-errorhandler emits. ADR-0016 accepts an assumed
        // contract behind an interface and requires it to be marked as assumed. This capture is the
        // only thing that would replace the assumption with a fact.
        assertBlocked("out/golden/PAR-002/unhandled-failures", "PAR-002-e");
    }

    @Test // PAR-002-f
    @DisplayName("f: the stale-status baseline that would close R-024 is blocked")
    void staleStatusBaselineIsBlocked() {
        assertBlocked("out/golden/PAR-002/stale-status", "PAR-002-f");
    }

    @Test // PAR-002-g
    @DisplayName("g: the observation confirming the inert targetValue parameter pass is blocked")
    void auditRecordCompletionBaselineIsBlocked() {
        // ADR-0034 treats the targetValue on the eight responseLogFlow call sites as inert and ports
        // nothing. "Probably inert" is a judgement; this capture is what would make it an observation.
        assertBlocked("out/golden/PAR-002/audit-record-completion", "PAR-002-g");
    }

    @Test // PAR-002-h
    @DisplayName("h: whether the shared logging module mutates the payload is still unknown (R-005)")
    void sharedLoggingSideEffectBaselineIsBlocked() {
        assertBlocked("out/golden/PAR-002/shared-logging-side-effects", "PAR-002-h");
    }

    @Test // PAR-002-i
    @DisplayName("i: no baseline containing a subject, an actor or agency entitlements has been stored")
    void noIdentityBearingBaselineHasBeenStored() {
        // Same shape as PAR-001-h and deliberately not merged with it: one governs customer data in
        // billing payloads, the other governs identity data in audit records, and they will be
        // signed off by different people.
        assertThat(ParityBaselines.all().values())
                .as("audit baselines carry authenticated subjects, actors and agency entitlements; "
                        + "none may be stored before R-029 agrees a treatment")
                .noneMatch(ParityBaselines.Entry::captured);
    }

    // ---------------------------------------------------------------------------------------------
    // The parity criteria owned by other tasks, each blocked on the two capture tasks above.
    // ---------------------------------------------------------------------------------------------

    @Test // ADM-004-g
    @DisplayName("ADM-004-g: the per-callsite audit-record baseline is blocked")
    void perCallsiteAuditRecordBaselineIsBlocked() {
        assertBlocked("out/golden/ADM-004/audit-record-per-callsite", "ADM-004-g");
    }

    @Test // AUD-001-j
    @DisplayName("AUD-001-j: the entry audit-record baseline, one per endpoint, is blocked")
    void entryAuditRecordBaselineIsBlocked() {
        assertBlocked("out/golden/AUD-001/entry-audit-record", "AUD-001-j");
    }

    @Test // AUD-004-c
    @DisplayName("AUD-004-c: the missing-entry-record baseline is blocked, so the target states its own behaviour")
    void missingEntryRecordBaselineIsBlocked() {
        // The criterion asks for the legacy behaviour "rather than silently substituting an empty
        // record". Without the capture, the target does the next most honest thing: N-0049's
        // epoch-poisoned elapsed time is reproduced exactly (asserted in AUD-004-b) so the anomaly
        // stays visible in dashboards instead of being smoothed away.
        assertBlocked("out/golden/AUD-004/missing-entry-record", "AUD-004-c");
    }

    @Test // AUD-006-h
    @DisplayName("AUD-006-h: the logging-step-failure baseline is blocked (R-005)")
    void loggingStepFailureBaselineIsBlocked() {
        assertBlocked("out/golden/AUD-006/logging-step-failure", "AUD-006-h");
    }

    @Test // ERR-001-h
    @DisplayName("ERR-001-h: no baseline exists for a backend failure on any of the five absorbing endpoints")
    void backendFailureBodyBaselineIsBlocked() {
        assertBlocked("out/golden/ERR-001/backend-failure-bodies", "ERR-001-h");
    }

    @Test // ERR-003-g
    @DisplayName("ERR-003-g: the unhandled-failure response baseline is blocked, including the timeout statuses")
    void unhandledFailureResponseBaselineIsBlocked() {
        assertBlocked("out/golden/ERR-003/unhandled-failure-responses", "ERR-003-g");
    }

    @Test // INF-001-f
    @DisplayName("INF-001-f: the /info baseline is blocked; the key set is asserted from the rule instead")
    void infoResponseBaselineIsBlocked() {
        assertBlocked("out/golden/INF-001/info-response", "INF-001-f");
    }

    @Test // SEC-001-f
    @DisplayName("SEC-001-f: the token-rejection baseline cannot be written at all until R-003 is answered")
    void tokenRejectionBaselineIsBlocked() {
        // SEC-001's own notes say the criterion "cannot be written until R-003 is answered": the API
        // Manager policy set was never exported, so the rejection body of the legacy is unknown. The
        // block here is on a different risk from every other entry, and the register says so.
        ParityBaselines.Entry entry = assertBlocked("out/golden/SEC-001/token-rejection", "SEC-001-f");
        assertThat(entry.blockedReason()).contains("R-003");
    }

    @Test // SEC-002-c
    @DisplayName("SEC-002-c: the entitled-caller worklist baseline is blocked")
    void entitledCallerWorklistBaselineIsBlocked() {
        // The criterion asks that an ENTITLED caller's response be "unchanged from the captured
        // legacy baseline" — i.e. that ADR-0037's new check does not disturb the happy path. The
        // no-backend-call half of that guarantee is asserted directly in AgencyEntitlementTest.
        assertBlocked("out/golden/SEC-002/entitled-caller-worklist", "SEC-002-c");
    }

    @Test // TOK-002-d
    @DisplayName("TOK-002-d: the malformed-STS WAS-request baseline is blocked on R-006")
    void malformedStsResponseWasBaselineIsBlocked() {
        ParityBaselines.Entry entry =
                assertBlocked("out/golden/TOK-002/malformed-sts-response-was", "TOK-002-d");
        assertThat(entry.blockedReason()).contains("R-006");
    }

    @Test // TOK-002-e
    @DisplayName("TOK-002-e: the malformed-STS ESB-request baseline is blocked on R-006")
    void malformedStsResponseEsbBaselineIsBlocked() {
        // d and e describe two DIFFERENT legacy outcomes from one root cause: the WAS request goes
        // out with the security header silently dropped, the ESB request with an empty assertion
        // element (N-0019 ec 1). ADR-0007 refuses to send either, so the migrated behaviour is a
        // deliberate divergence — which makes capturing what the legacy did more important, not less,
        // because the divergence cannot be described without it.
        ParityBaselines.Entry entry =
                assertBlocked("out/golden/TOK-002/malformed-sts-response-esb", "TOK-002-e");
        assertThat(entry.blockedReason()).contains("R-006");
    }

    // ---------------------------------------------------------------------------------------------

    /** Every ref must be declared, labelled with its criterion, and honestly labelled as uncaptured. */
    private static ParityBaselines.Entry assertBlocked(String ref, String criterionId) {
        ParityBaselines.Entry entry = ParityBaselines.find(ref).orElseThrow(() ->
                new AssertionError("golden_payload_ref '" + ref + "' is named by " + criterionId
                        + " in WP-001 but is not declared in the parity register. Every ref the packet "
                        + "names must be accounted for, including the ones with no evidence behind them."));
        assertThat(entry.criterionId()).isEqualTo(criterionId);
        assertThat(entry.captured())
                .as("%s claims a capture; supply the payload or leave it declared as blocked", ref)
                .isFalse();
        assertThat(ParityBaselines.isVerifiedParity(ref))
                .as("%s must never be reported as verified parity while no capture exists (ADR-0021)", ref)
                .isFalse();
        assertThat(entry.payload().origin())
                .as("%s has no legacy capture, so it cannot be labelled CAPTURED_LEGACY", ref)
                .isNotEqualTo(BaselineOrigin.CAPTURED_LEGACY);
        return entry;
    }

    /** The legacy fixture sets PAR-001-g excludes by name, with the reason each is excluded. */
    private static final List<ExcludedFixture> EXCLUDED_LEGACY_FIXTURES = List.of(
            new ExcludedFixture("legacy MUnit suite (compares the mock to itself)",
                    "The suite stubs the back end and asserts the stub's own response, so it passes "
                            + "for any flow behaviour. It is vacuous as evidence."),
            new ExcludedFixture("stale fixture set: pre-escrow account summary",
                    "Documents a response shape the API no longer serves; matching it would assert a "
                            + "contract that has already changed."),
            new ExcludedFixture("stale fixture set: pre-BCMS transactions",
                    "Same: a previous contract, not current behaviour."));

    private record ExcludedFixture(String name, String reason) {
    }
}
