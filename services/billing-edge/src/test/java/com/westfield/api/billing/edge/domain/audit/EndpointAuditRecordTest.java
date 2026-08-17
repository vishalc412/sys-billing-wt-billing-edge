package com.westfield.api.billing.edge.domain.audit;

import com.westfield.api.billing.edge.testsupport.Fixtures;
import com.westfield.api.billing.platform.spi.CallerContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AUD-005 — the per-endpoint audit record (N-0036, N-0043, N-0045).
 *
 * <p>The legacy carries FIVE verbatim copies of this projection, differing only in one string
 * literal. ADR-0017 consolidates them; these tests assert that the consolidation changed no emitted
 * value, which is the whole justification for doing it.
 */
@DisplayName("AUD-005 per-endpoint audit record")
class EndpointAuditRecordTest {

    private static Map<String, Object> recordFor(CallerContext caller, String uriTemplate, String apiKey) {
        return EndpointAuditRecord.fields("experience-api", "sapi-billing", "v1", "GET",
                "/pastDueToday/A0421?environment=TEST", uriTemplate, apiKey, caller);
    }

    @Test // AUD-005-a
    @DisplayName("a: the record identifies the calling system, the API, the subject, the client, the verb and both URIs")
    void recordIdentifiesTheCallAndTheCaller() {
        Map<String, Object> fields =
                recordFor(Fixtures.fullyPopulatedCaller(), "/pastDueToday/{agencyCode}", "an-api-key");

        assertThat(fields.get("callingSystem")).isEqualTo("experience-api");
        assertThat(fields.get("apiName")).isEqualTo("sapi-billing");
        assertThat(fields.get("apiVersion")).isEqualTo("v1");
        assertThat(fields.get("sub")).isEqualTo("u123456");
        assertThat(fields.get("clientId")).isEqualTo("client-abc");
        assertThat(fields.get("httpMethod")).isEqualTo("GET");
        assertThat(fields.get("requestUri")).isEqualTo("/pastDueToday/A0421?environment=TEST");
        assertThat(fields.get("uriTemplate")).isEqualTo("/pastDueToday/{agencyCode}");
    }

    @Test // AUD-005-b
    @DisplayName("b: an actor claim marks the record impersonated and records the actor beside the real subject")
    void actorIsRecordedBesideTheRealSubject() {
        // The per-endpoint stream uses the INCOMPLETE rule, so it recognises the nested actor object
        // only. A flat actSub token is reported as not impersonated here and as impersonated in the
        // request/response stream; that disagreement is AUD-003-h and ships deliberately (ADR-0036).
        CallerContext caller = Fixtures.nestedActorCaller("supervisor-1", "supervisor@westfieldgrp.com");

        Map<String, Object> fields = recordFor(caller, "/pastDueToday/{agencyCode}", "an-api-key");

        assertThat(fields.get("impersonated")).isEqualTo(true);
        assertThat(fields.get("actSub")).isEqualTo("supervisor-1");
        assertThat(fields.get("actEmail")).isEqualTo("supervisor@westfieldgrp.com");
        assertThat(fields.get("sub")).isEqualTo("u123456");
    }

    @Test // AUD-005-c
    @DisplayName("c: an omitted optional identity field carries EMPTY rather than being dropped")
    void omittedFieldsCarryTheSentinel() {
        CallerContext caller = new CallerContext(null, null, "client-abc", null, null, false, List.of());

        Map<String, Object> fields = recordFor(caller, "/pastDueToday/{agencyCode}", null);

        // Every record has the same field set, so a log consumer never has to handle a missing key.
        assertThat(fields.get("sub")).isEqualTo(CallerContext.EMPTY);
        assertThat(fields.get("email")).isEqualTo(CallerContext.EMPTY);
        assertThat(fields.get("actSub")).isEqualTo(CallerContext.EMPTY);
        assertThat(fields.get("actEmail")).isEqualTo(CallerContext.EMPTY);
        assertThat(fields.get("apiKey")).isEqualTo(CallerContext.EMPTY);
    }

    @Test // AUD-005-d
    @DisplayName("d: an explicit null for an optional field is indistinguishable from an absent one")
    void explicitNullCollapsesOntoAbsent() {
        // The legacy 'default "EMPTY"' idiom fires on an absent key AND on an explicit null
        // (N-0036 ec 2). A null-only check would not reproduce it, so the two collapse together.
        Map<String, Object> absent = recordFor(
                new CallerContext("u1", null, "c", null, null, false, List.of()),
                "/pastDueToday/{agencyCode}", "k");
        Map<String, Object> explicitlyNull = recordFor(
                new CallerContext("u1", "", "c", "", "", false, List.of()),
                "/pastDueToday/{agencyCode}", "k");

        assertThat(explicitlyNull.get("email")).isEqualTo(absent.get("email")).isEqualTo(CallerContext.EMPTY);
        assertThat(explicitlyNull.get("actSub")).isEqualTo(absent.get("actSub")).isEqualTo(CallerContext.EMPTY);
    }

    @Test // AUD-005-e
    @DisplayName("e: with no decoded token context the client id is null while every other field is EMPTY")
    void clientIdIsTheOneFieldWithoutADefault() {
        Map<String, Object> fields = recordFor(null, "/pastDueToday/{agencyCode}", "an-api-key");

        assertThat(fields).containsKey("clientId");
        assertThat(fields.get("clientId")).isNull();
        assertThat(fields.get("sub")).isEqualTo(CallerContext.EMPTY);
        assertThat(fields.get("email")).isEqualTo(CallerContext.EMPTY);
        assertThat(fields.get("impersonated")).isEqualTo(false);
    }

    @Test // AUD-005-h
    @DisplayName("h: the API key header is captured even though no API-key policy protects this service")
    void apiKeyHeaderIsCaptured() {
        // No API-key policy appears anywhere in the legacy source, yet the header is read and logged
        // on all five copies of this projection (N-0036 ec 5) — presumably supplied by an upstream
        // experience API. Dropping it would silently remove a field a log consumer may key on, and
        // nobody can currently say who consumes it (R-019).
        Map<String, Object> fields =
                recordFor(Fixtures.fullyPopulatedCaller(), "/pastDueToday/{agencyCode}", "abc-123-key");

        assertThat(fields.get("apiKey")).isEqualTo("abc-123-key");
    }

    @Test
    @DisplayName("the five endpoints share one implementation, so their field sets cannot drift apart")
    void oneImplementationForFiveEndpoints() {
        CallerContext caller = Fixtures.fullyPopulatedCaller();
        List<String> templates = List.of(
                "/externalPrimaryAccount/{billingAccountNumber}/account-billing",
                "/externalPrimaryAccount/{policyNumber}/policy-billing",
                "/pastDueToday/{agencyCode}",
                "/pendingCancelToday/{agencyCode}",
                "/info");

        Map<String, Object> reference = recordFor(caller, templates.get(0), "k");
        for (String template : templates) {
            Map<String, Object> fields = recordFor(caller, template, "k");
            assertThat(fields.keySet()).isEqualTo(reference.keySet());
            assertThat(fields.get("uriTemplate")).isEqualTo(template);
        }
    }
}
