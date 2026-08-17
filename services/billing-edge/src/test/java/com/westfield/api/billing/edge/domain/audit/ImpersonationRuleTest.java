package com.westfield.api.billing.edge.domain.audit;

import com.westfield.api.billing.edge.adapter.in.web.CallerContextFactory;
import com.westfield.api.billing.edge.testsupport.Fixtures;
import com.westfield.api.billing.platform.spi.CallerContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AUD-003 — impersonation determination and actor attribution (N-0036, N-0051, N-0052).
 *
 * <p>ADR-0036 decided PRESERVE: both definitions of "impersonated" ship, each implemented once. They
 * disagree, and the disagreement is asserted here rather than removed. Reconciling them would move a
 * compliance-relevant count in the direction of "more impersonation than we thought", and ADR-0036
 * gates that on R-021 producing a named compliance owner, which has not happened.
 *
 * <p>None of this has any legacy test coverage (R-020); PAR-002-b is the capture that would make it
 * parity evidence and is blocked.
 */
@DisplayName("AUD-003 impersonation and actor attribution")
class ImpersonationRuleTest {

    private final CallerContextFactory factory = new CallerContextFactory();

    private static Map<String, Object> claims(Map<String, Object> extra) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "u123456");
        claims.put("email", "adjuster@westfieldgrp.com");
        claims.put("clientId", "client-abc");
        claims.putAll(extra);
        return claims;
    }

    @Test // AUD-003-a
    @DisplayName("a: a nested actor object means impersonated, with the actor read from that object")
    void nestedActorIsImpersonated() {
        CallerContext caller = factory.from(Fixtures.jwt(claims(Map.of(
                "act", Map.of("sub", "supervisor-1", "email", "supervisor@westfieldgrp.com")))));

        assertThat(ImpersonationRule.complete(caller)).isTrue();
        assertThat(caller.actorSubject()).isEqualTo("supervisor-1");
        assertThat(caller.actorEmail()).isEqualTo("supervisor@westfieldgrp.com");
    }

    @Test // AUD-003-b
    @DisplayName("b: a flat actor-subject claim means impersonated, with the actor read from the flat claims")
    void flatActorIsImpersonatedUnderTheCompleteRule() {
        CallerContext caller = factory.from(Fixtures.jwt(claims(Map.of(
                "actSub", "supervisor-2", "actEmail", "supervisor2@westfieldgrp.com"))));

        // PingFederate encodes the actor differently depending on the issuing channel, so both
        // encodings count under the complete rule (N-0052).
        assertThat(ImpersonationRule.complete(caller)).isTrue();
        assertThat(caller.actorSubject()).isEqualTo("supervisor-2");
        assertThat(caller.actorEmail()).isEqualTo("supervisor2@westfieldgrp.com");
    }

    @Test // AUD-003-c
    @DisplayName("c: a flat actor-subject carrying the four-character text 'null' is NOT impersonated")
    void flatActorStringNullIsNotAnActor() {
        CallerContext caller = factory.from(Fixtures.jwt(claims(Map.of("actSub", "null"))));

        // Some issuers write the four-character STRING "null" as their no-actor marker. It is not a
        // JSON null and it must not be mistaken for an actor (N-0052).
        assertThat(caller.actorSubject()).isEqualTo(CallerContext.NULL_SENTINEL);
        assertThat(ImpersonationRule.complete(caller)).isFalse();
    }

    @Test // AUD-003-d
    @DisplayName("d: a nested actor whose subject is the text 'null' IS impersonated — the asymmetry survives")
    void nestedActorStringNullIsStillImpersonated() {
        CallerContext caller = factory.from(Fixtures.jwt(claims(Map.of(
                "act", Map.of("sub", "null")))));

        // The same four characters mean "no actor" in one encoding and "an actor called null" in the
        // other. That is legacy behaviour and it is load-bearing until an ADR says otherwise: the
        // complete rule short-circuits on the PRESENCE of the act object without inspecting its
        // contents, and only the flat branch tests the sentinel (N-0052 ec 1).
        assertThat(ImpersonationRule.complete(caller)).isTrue();
    }

    @Test // AUD-003-e
    @DisplayName("e: a token carrying neither representation is not impersonated")
    void noActorIsNotImpersonated() {
        CallerContext caller = factory.from(Fixtures.jwt(claims(Map.of())));

        assertThat(ImpersonationRule.complete(caller)).isFalse();
        assertThat(ImpersonationRule.actObjectOnly(caller)).isFalse();
    }

    @Test // AUD-003-f
    @DisplayName("f: a nested actor missing sub or email records EMPTY and does not fall back to the flat claims")
    void nestedActorDoesNotFallBackToFlatClaims() {
        CallerContext caller = factory.from(Fixtures.jwt(claims(Map.of(
                "act", Map.of("sub", "supervisor-3"),
                // Present, and deliberately NOT consulted: the nested branch wins outright.
                "actEmail", "this-value-must-not-appear@westfieldgrp.com"))));

        assertThat(caller.actorSubject()).isEqualTo("supervisor-3");
        assertThat(caller.actorEmail()).isNull();

        // The projection turns the missing value into EMPTY. Adding the fallback would report actor
        // emails the legacy never reported, which is a change to a compliance-relevant field.
        Map<String, Object> fields = auditRecordFor(caller).entryFields();
        assertThat(fields.get("actEmail")).isEqualTo(CallerContext.EMPTY);
        assertThat(fields.get("actSub")).isEqualTo("supervisor-3");
    }

    @Test // AUD-003-g
    @DisplayName("g: with no actor at all the actor fields are present and carry EMPTY rather than being omitted")
    void actorFieldsArePresentAndEmptyRatherThanAbsent() {
        CallerContext caller = factory.from(Fixtures.jwt(claims(Map.of())));

        Map<String, Object> fields = auditRecordFor(caller).entryFields();

        assertThat(fields).containsKey("actSub").containsKey("actEmail");
        assertThat(fields.get("actSub")).isEqualTo(CallerContext.EMPTY);
        assertThat(fields.get("actEmail")).isEqualTo(CallerContext.EMPTY);
    }

    @Test // AUD-003-h
    @DisplayName("h: INVERTED per ADR-0036 — the two audit streams DISAGREE for a flat-actor token, deliberately")
    void theTwoStreamsDisagreeForAFlatActorToken() {
        // WP-001 wrote this criterion against ADR-CANDIDATE-0036 option B (reconcile the two rules).
        // The architect selected option A, PRESERVE, so the criterion inverts exactly as the packet's
        // note anticipates: the assertion is that the disagreement SURVIVES and is deliberate.
        //
        // For a token using the flat actSub representation:
        //   - the request/response stream says impersonated  (complete rule, N-0052)
        //   - the per-endpoint stream says NOT impersonated  (!isEmpty(act) only, N-0036 ec 4)
        // Both are emitted for the same request. A compliance report that counts impersonated calls
        // gets two different answers depending on which stream it reads, and it does today.
        CallerContext caller = factory.from(Fixtures.jwt(claims(Map.of(
                "actSub", "supervisor-2", "actEmail", "supervisor2@westfieldgrp.com"))));

        Map<String, Object> requestResponseRecord = auditRecordFor(caller).entryFields();
        Map<String, Object> perEndpointRecord = EndpointAuditRecord.fields(
                "experience-api", "sapi-billing", "v1", "GET",
                "/pastDueToday/A0421", "/pastDueToday/{agencyCode}", "an-api-key", caller);

        assertThat(requestResponseRecord.get("impersonated")).isEqualTo(true);
        assertThat(perEndpointRecord.get("impersonated")).isEqualTo(false);
        assertThat(requestResponseRecord.get("impersonated"))
                .as("ADR-0036 PRESERVE: the disagreement ships and is not to be 'fixed' without a "
                        + "named compliance owner (R-021)")
                .isNotEqualTo(perEndpointRecord.get("impersonated"));

        // Both streams nonetheless attribute the SAME actor, so the identity is never in doubt —
        // only the boolean disagrees.
        assertThat(perEndpointRecord.get("actSub")).isEqualTo(requestResponseRecord.get("actSub"));
    }

    @Test
    @DisplayName("a nested actor object satisfies both rules, so the two streams agree in that case")
    void thetwoStreamsAgreeForANestedActorToken() {
        CallerContext caller = factory.from(Fixtures.jwt(claims(Map.of(
                "act", Map.of("sub", "supervisor-1", "email", "supervisor@westfieldgrp.com")))));

        assertThat(ImpersonationRule.complete(caller)).isTrue();
        assertThat(ImpersonationRule.actObjectOnly(caller)).isTrue();
    }

    private static RequestResponseAuditRecord auditRecordFor(CallerContext caller) {
        return new RequestResponseAuditRecord(
                OffsetDateTime.parse("2026-08-16T10:00:00Z"), 1_755_338_400_000L,
                "sapi-billing", "v1", "corr-1", "interaction-1", "/pastDueToday/A0421", caller);
    }
}
