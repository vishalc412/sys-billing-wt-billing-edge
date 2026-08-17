package com.westfield.api.billing.edge.domain.audit;

import com.westfield.api.billing.edge.testsupport.Fixtures;
import com.westfield.api.billing.platform.spi.CallerContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AUD-001 — request/response audit record construction (N-0050, N-0051).
 * AUD-004 — its completion with status and elapsed time (N-0048, N-0049).
 *
 * <p>ADR-0017 makes the FIELD SET and the SENTINELS a contract. These tests assert that contract,
 * including the two inconsistencies it deliberately preserves: {@code clientId} has no default while
 * every neighbour does, and an absent agency list is an empty list rather than the EMPTY sentinel.
 */
@DisplayName("AUD-001 / AUD-004 request-response audit record")
class RequestResponseAuditRecordTest {

    private static final OffsetDateTime ENTRY_AT = OffsetDateTime.parse("2026-08-16T10:00:00Z");
    private static final long ENTRY_MILLIS = 1_755_338_400_000L;
    private static final String RAW_URI = "/externalPrimaryAccount/1234567890/account-billing?environment=TEST";

    private RequestResponseAuditRecord record(CallerContext caller) {
        return new RequestResponseAuditRecord(ENTRY_AT, ENTRY_MILLIS, "sapi-billing", "v1",
                "corr-1", "interaction-1", RAW_URI, caller);
    }

    @Test // AUD-001-a
    @DisplayName("a: the entry record carries the timestamp, the fixed marker, the API name, version and layer")
    void entryRecordCarriesTheFixedIdentity() {
        Map<String, Object> fields = record(Fixtures.fullyPopulatedCaller()).entryFields();

        assertThat(fields.get("entryTimestamp")).isEqualTo(ENTRY_AT.toString());
        assertThat(fields.get("identifier")).isEqualTo(RequestResponseAuditRecord.IDENTIFIER);
        assertThat(fields.get("apiName")).isEqualTo("sapi-billing");
        assertThat(fields.get("apiVersion")).isEqualTo("v1");
        assertThat(fields.get("apiLayer")).isEqualTo(RequestResponseAuditRecord.API_LAYER);
    }

    @Test // AUD-001-b
    @DisplayName("b: the interaction correlation id and the service's own id are separate fields")
    void bothCorrelationIdentifiersAreRecordedSeparately() {
        Map<String, Object> fields = record(Fixtures.fullyPopulatedCaller()).entryFields();

        // Neither substitutes for the other: one stitches this request's log lines together, the
        // other stitches several API calls into one business interaction (N-0051 rule 2).
        assertThat(fields.get("correlationId")).isEqualTo("corr-1");
        assertThat(fields.get("interactionCorrelationId")).isEqualTo("interaction-1");
    }

    @Test // AUD-001-c
    @DisplayName("c: subject, email, client id and agency codes are recorded with the raw and masked URIs")
    void identityAndUriAreRecorded() {
        Map<String, Object> fields = record(Fixtures.fullyPopulatedCaller()).entryFields();

        assertThat(fields.get("sub")).isEqualTo("u123456");
        assertThat(fields.get("email")).isEqualTo("adjuster@westfieldgrp.com");
        assertThat(fields.get("clientId")).isEqualTo("client-abc");
        assertThat(fields.get("agencyCodes")).isEqualTo(List.of("A0421", "A0999"));
        assertThat(fields.get("requestUri")).isEqualTo(RAW_URI);
        assertThat(fields.get("requestTemplate")).isEqualTo("/externalPrimaryAccount/X/account-billing");
    }

    @Test // AUD-001-d
    @DisplayName("d: absent optional fields are the literal EMPTY; an absent agency list is an empty list")
    void absentFieldsUseTheSentinelAndTheListDoesNot() {
        CallerContext sparse = new CallerContext(null, null, "client-abc", null, null, false, List.of());

        Map<String, Object> fields = record(sparse).entryFields();

        assertThat(fields.get("sub")).isEqualTo(CallerContext.EMPTY);
        assertThat(fields.get("email")).isEqualTo(CallerContext.EMPTY);
        assertThat(fields.get("actSub")).isEqualTo(CallerContext.EMPTY);
        assertThat(fields.get("actEmail")).isEqualTo(CallerContext.EMPTY);
        // NOT "EMPTY". The list-valued field uses an empty list, and that asymmetry is the contract
        // (N-0051 rule 5) — a consumer iterating the field must never receive a String.
        assertThat(fields.get("agencyCodes")).isEqualTo(List.of());
    }

    @Test // AUD-001-e
    @DisplayName("e: with no decoded token context the client id is null while its neighbours are EMPTY")
    void clientIdIsNullWhenTheTokenPolicyDidNotRun() {
        Map<String, Object> fields = record(null).entryFields();

        // Preserved deliberately (N-0051 ec 1). clientId is the one field the legacy leaves without a
        // default, which makes it the only usable detector for "the token policy did not run" — a
        // condition that is otherwise invisible because every other field says EMPTY either way.
        assertThat(fields).containsKey("clientId");
        assertThat(fields.get("clientId")).isNull();
        assertThat(fields.get("sub")).isEqualTo(CallerContext.EMPTY);
        assertThat(fields.get("email")).isEqualTo(CallerContext.EMPTY);
        assertThat(fields.get("agencyCodes")).isEqualTo(List.of());
    }

    @Test // AUD-001-f
    @DisplayName("f: the correlation id is always supplied, so its EMPTY fallback is never reached")
    void correlationIdFallbackIsUnreachableInPractice() {
        assertThat(record(Fixtures.fullyPopulatedCaller()).correlationId()).isEqualTo("corr-1");

        // The fallback is kept so the field can never be absent, but the runtime always supplies a
        // value, so this branch does not fire in production (N-0051 ec 4). Asserting it here records
        // that the branch exists and is dead rather than leaving a reader to wonder.
        RequestResponseAuditRecord withoutRuntimeId = new RequestResponseAuditRecord(
                ENTRY_AT, ENTRY_MILLIS, "sapi-billing", "v1", null, null, RAW_URI, null);
        assertThat(withoutRuntimeId.correlationId()).isEqualTo(CallerContext.EMPTY);
    }

    @Test // AUD-001-g
    @DisplayName("g: the entry timestamp and the entry millis are independent readings and may differ")
    void timestampAndMillisAreIndependentReadings() {
        // Captured at slightly different points inside one legacy expression (N-0051 ec 5). Neither
        // is derived from the other, so a small disagreement between them is correct. Deriving one
        // from the other would look tidier and would quietly change a field two dashboards read.
        long millisThatDisagreeWithTheTimestamp = ENTRY_MILLIS + 7;
        RequestResponseAuditRecord skewed = new RequestResponseAuditRecord(
                ENTRY_AT, millisThatDisagreeWithTheTimestamp, "sapi-billing", "v1",
                "corr-1", "interaction-1", RAW_URI, Fixtures.fullyPopulatedCaller());

        Map<String, Object> fields = skewed.entryFields();

        assertThat(fields.get("entryTimestamp")).isEqualTo(ENTRY_AT.toString());
        assertThat(fields.get("entryTimeMillis")).isEqualTo(millisThatDisagreeWithTheTimestamp);
        assertThat(ENTRY_AT.toInstant().toEpochMilli()).isNotEqualTo(millisThatDisagreeWithTheTimestamp);
    }

    @Test // AUD-004-b
    @DisplayName("b: an unpopulated entry timestamp yields the full epoch figure rather than an error")
    void elapsedTimeIsEpochPoisonedRatherThanFailing() {
        // The legacy 'default 0' on the entry timestamp makes the elapsed figure the entire
        // epoch-milliseconds value (N-0049 ec 1). It is nonsense, and it is NON-FAILING nonsense —
        // a broken audit record must never break a request. The poisoned value is visible in
        // dashboards, and that visibility is the intended signal.
        RequestResponseAuditRecord noEntryTime = new RequestResponseAuditRecord(
                null, 0L, "sapi-billing", "v1", "corr-1", "interaction-1", RAW_URI,
                Fixtures.fullyPopulatedCaller());

        Map<String, Object> completed = noEntryTime.completedFields(200, ENTRY_MILLIS, false);

        assertThat(completed.get("elapsedTime")).isEqualTo(ENTRY_MILLIS);
        assertThat(completed.get("responseStatusCode")).isEqualTo(200);
    }

    @Test // AUD-004-d
    @DisplayName("d: an undecided status is recorded as the accepted sentinel, not an arbitrary default")
    void undecidedStatusUsesTheAcceptedSentinel() {
        // ADR-0035 replaces the legacy bare null with an explicit sentinel consistent with the EMPTY
        // convention every neighbouring field already uses. Choosing 0, or 500, or omitting the key
        // would each be an invention; the sentinel says "not known" in the vocabulary the record
        // already speaks.
        Map<String, Object> completed =
                record(Fixtures.fullyPopulatedCaller()).completedFields(null, ENTRY_MILLIS + 12, true);

        assertThat(completed.get("responseStatusCode")).isEqualTo(RequestResponseAuditRecord.STATUS_UNKNOWN);
        assertThat(RequestResponseAuditRecord.STATUS_UNKNOWN).isEqualTo(CallerContext.EMPTY);
        assertThat(completed.get("unhandledFailure")).isEqualTo(true);
    }

    @Test
    @DisplayName("the completed record is the entry record plus three fields and nothing else")
    void completionAddsExactlyThreeFields() {
        RequestResponseAuditRecord record = record(Fixtures.fullyPopulatedCaller());

        Map<String, Object> entry = record.entryFields();
        Map<String, Object> completed = record.completedFields(200, ENTRY_MILLIS + 40, false);

        assertThat(completed.keySet())
                .containsAll(entry.keySet())
                .contains("responseStatusCode", "elapsedTime", "unhandledFailure")
                .hasSize(entry.size() + 3);
        assertThat(completed.get("elapsedTime")).isEqualTo(40L);
    }
}
