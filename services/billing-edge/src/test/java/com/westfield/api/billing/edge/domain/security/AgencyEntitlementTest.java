package com.westfield.api.billing.edge.domain.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westfield.api.billing.edge.adapter.in.web.AgencyEntitlementFilter;
import com.westfield.api.billing.edge.adapter.in.web.RequestFunnel;
import com.westfield.api.billing.edge.adapter.out.flags.ConfiguredLegacyBehaviourFlags;
import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.edge.domain.audit.EndpointAuditRecord;
import com.westfield.api.billing.edge.domain.audit.RequestResponseAuditRecord;
import com.westfield.api.billing.edge.testsupport.Fixtures;
import com.westfield.api.billing.edge.testsupport.TestFilterChain;
import com.westfield.api.billing.platform.spi.CallerContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-002 — agency entitlement on the two worklist endpoints (N-0035 ec 2, N-0039 ec 2, N-0041 ec 2,
 * ADR-0037).
 *
 * <p><b>This is the one task in this packet that implements behaviour the legacy does not have</b>,
 * and it is worth being precise about what that means. Today
 * {@code /pastDueToday/{agencyCode}} and {@code /pendingCancelToday/{agencyCode}} serve ANY agency's
 * worklist to ANY authenticated caller. The token carries an {@code agencyCodes} claim; the legacy
 * reads it and uses it to write a log line. Nothing authorises anything. The data at stake is the
 * named insured's name and postal address, the policy number, the overdue amount and the
 * cancellation date — the knowledge map calls this the highest-consequence unknown in the
 * application.
 *
 * <p>ADR-0037 is assumption-based and the architect flagged it as the weakest decision in the set,
 * because the API Manager policy set that might already enforce this was never exported (R-003). If a
 * gateway policy does enforce it, this check is redundant and harmless. If it does not, this check is
 * the only thing standing between one agency and another's customers. Given that asymmetry, three
 * properties of the rule are part of the decision rather than implementation choices, and each has a
 * test below:
 * <ul>
 *   <li>an ABSENT or EMPTY claim is a DENY (d) — the strict reading, deliberately chosen;</li>
 *   <li>exemptions are named, owned and dated, so cross-agency access is a list someone signed;</li>
 *   <li>{@code log-only} (e) exists so that shipping without enforcement is a decision a named human
 *       made, recorded and counted, rather than an engineer's default.</li>
 * </ul>
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("SEC-002 agency entitlement enforcement")
class AgencyEntitlementTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private BillingEdgeProperties properties;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        properties = Fixtures.validProperties();
        meterRegistry = new SimpleMeterRegistry();
    }

    private AgencyEntitlementFilter filterWith(Set<String> exemptClientIds) {
        return new AgencyEntitlementFilter(
                new AgencyEntitlementRule(exemptClientIds),
                new ConfiguredLegacyBehaviourFlags(properties),
                objectMapper,
                meterRegistry);
    }

    /** Runs one worklist request through the filter and reports whether it reached the backend. */
    private Outcome request(String path, CallerContext caller) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestFunnel.putCorrelationId(request, "corr-1");
        RequestFunnel.putCaller(request, caller);

        TestFilterChain chain = new TestFilterChain(TestFilterChain.doNothing(), filterWith(Set.of()));
        chain.doFilter(request, response);
        return new Outcome(response, chain.terminalReached());
    }

    private record Outcome(MockHttpServletResponse response, boolean reachedBackend) {
    }

    @Test // SEC-002-a
    @DisplayName("a: an unentitled past-due-today request is 403 and makes no backend call")
    void unentitledPastDueTodayIsDenied() throws Exception {
        // The caller holds A0421 and asks for A0999. Denial happens at the edge, before routing to
        // the implementation, so the guarantee is "no backend call" rather than "the response was
        // discarded" — a check inside the endpoint would still have fetched the other agency's data.
        CallerContext caller = new CallerContext("u1", "u1@westfieldgrp.com", "client-abc",
                CallerContext.EMPTY, CallerContext.EMPTY, false, List.of("A0421"));

        Outcome outcome = request("/pastDueToday/A0999", caller);

        assertThat(outcome.response().getStatus()).isEqualTo(403);
        assertThat(outcome.reachedBackend())
                .as("no backend call may be made for a request that will be refused")
                .isFalse();
        assertThat(outcome.response().getContentType()).isEqualTo("application/problem+json");

        Map<String, Object> body = objectMapper.readValue(
                outcome.response().getContentAsString(), new TypeReference<>() {
                });
        assertThat(body).containsEntry("status", 403);
        assertThat(body).containsEntry("correlationId", "corr-1");
        assertThat(String.valueOf(body.get("detail"))).contains("A0999");

        assertThat(meterRegistry.counter("billing.security.agency_entitlement_denied",
                "clientId", "client-abc").count()).isEqualTo(1);
    }

    @Test // SEC-002-b
    @DisplayName("b: an unentitled pending-cancel-today request is 403 and makes no backend call")
    void unentitledPendingCancelTodayIsDenied() throws Exception {
        // The second worklist endpoint, asserted separately rather than parameterised: the legacy's
        // two endpoints are two independent copies of the same gap (N-0039 ec 2 and N-0041 ec 2), and
        // a single rule now covers both. If someone later wires only one of them, this fails.
        CallerContext caller = new CallerContext("u1", "u1@westfieldgrp.com", "client-abc",
                CallerContext.EMPTY, CallerContext.EMPTY, false, List.of("A0421"));

        Outcome outcome = request("/pendingCancelToday/A0999", caller);

        assertThat(outcome.response().getStatus()).isEqualTo(403);
        assertThat(outcome.reachedBackend()).isFalse();

        // And the entitled caller is unaffected on the same endpoint — the check must not be a blanket
        // refusal that happens to pass the test above.
        Outcome entitled = request("/pendingCancelToday/A0421", caller);
        assertThat(entitled.response().getStatus()).isEqualTo(200);
        assertThat(entitled.reachedBackend()).isTrue();
    }

    @Test // SEC-002-d
    @DisplayName("d: a token with no agency claim at all is denied, identically on both endpoints")
    void absentClaimIsDeniedByDefault() throws Exception {
        // The decision that most deserves to be explicit. An absent or empty claim could plausibly
        // mean "unrestricted" — plenty of systems read it that way — and ADR-0037 chose the strict
        // reading: a caller with no entitlements has no agency worklist. Deny-by-default is the only
        // reading that fails safe when the claim is missing because something upstream broke.
        CallerContext noClaim = new CallerContext("u1", "u1@westfieldgrp.com", "client-abc",
                CallerContext.EMPTY, CallerContext.EMPTY, false, List.of());

        for (String path : List.of("/pastDueToday/A0421", "/pendingCancelToday/A0421")) {
            Outcome outcome = request(path, noClaim);
            assertThat(outcome.response().getStatus())
                    .as("%s must deny a caller with no entitlements", path)
                    .isEqualTo(403);
            assertThat(outcome.reachedBackend()).isFalse();
        }

        // No decoded token context at all is the same answer, by the same route.
        AgencyEntitlementRule rule = new AgencyEntitlementRule(Set.of());
        assertThat(rule.decide("A0421", null, true)).isEqualTo(AgencyEntitlementDecision.DENIED);
        assertThat(rule.decide("A0421", noClaim, true)).isEqualTo(AgencyEntitlementDecision.DENIED);
        assertThat(AgencyEntitlementDecision.DENIED.served()).isFalse();
    }

    @Test // SEC-002-e
    @DisplayName("e: with enforcement disabled the request is served and a distinct counter records the exposure")
    void logOnlyModeServesAndCountsTheExposure() throws Exception {
        // log-only is not "enforcement off". It is "the exposure persists and is measured while it
        // does", with its own counter so that "served without entitlement" is never confused with
        // "denied" on a dashboard. ADR-0037 requires a named human owner to select it, and the
        // startup validator logs a WARN when it is in force.
        properties.getSecurity().setAgencyEntitlement(BillingEdgeProperties.Security.Mode.LOG_ONLY);

        CallerContext caller = new CallerContext("u1", "u1@westfieldgrp.com", "client-abc",
                CallerContext.EMPTY, CallerContext.EMPTY, false, List.of("A0421"));

        Outcome outcome = request("/pastDueToday/A0999", caller);

        assertThat(outcome.response().getStatus()).isEqualTo(200);
        assertThat(outcome.reachedBackend())
                .as("log-only serves the request — that is the whole point, and the reason it needs an owner")
                .isTrue();

        assertThat(meterRegistry.counter("billing.security.agency_entitlement_unentitled_served",
                "clientId", "client-abc").count())
                .as("the exposure must be countable while it persists")
                .isEqualTo(1);
        assertThat(meterRegistry.counter("billing.security.agency_entitlement_denied",
                "clientId", "client-abc").count())
                .as("served and denied are different facts and must not share a counter")
                .isEqualTo(0);

        // And enforce is the default in every profile, including production, so reaching this branch
        // takes a deliberate configuration change.
        assertThat(new BillingEdgeProperties().getSecurity().getAgencyEntitlement())
                .isEqualTo(BillingEdgeProperties.Security.Mode.ENFORCE);
    }

    @Test // SEC-002-f
    @DisplayName("f: the caller's agency entitlements are audited exactly as today, enforcement or not")
    void entitlementsAreAuditedUnchangedInEitherMode() {
        // ADR-0037 adds an authorisation decision. It changes NOTHING about the audit record: the
        // agencyCodes field is projected exactly as the legacy projects it, in both streams, whether
        // enforcement is on, off, or the request was denied. An audit stream that changed shape
        // alongside a security change would make it impossible to tell which of the two moved a
        // downstream report.
        CallerContext caller = new CallerContext("u1", "u1@westfieldgrp.com", "client-abc",
                CallerContext.EMPTY, CallerContext.EMPTY, false, List.of("A0421", "A0999"));

        Map<String, Object> requestResponse = new RequestResponseAuditRecord(
                OffsetDateTime.parse("2026-08-17T09:30:00Z"), 1L, "sapi-billing", "v1",
                "corr-1", "interaction-1", "/pastDueToday/A0999", caller).entryFields();

        assertThat(requestResponse)
                .as("the raw list, in order, exactly as the legacy records it (N-0051 rule 4)")
                .containsEntry("agencyCodes", List.of("A0421", "A0999"));

        // The PER-ENDPOINT record does not carry the entitlements at all, and must not start to.
        // N-0036 lists what that projection records — calling system, API identity, subject, OAuth
        // client, method, raw URI, matched template, API key and impersonation — and agencyCodes is
        // not among them. Adding it here would look like an improvement and would change the field
        // set of a log stream that ADR-0017 makes a contract.
        Map<String, Object> perEndpoint = EndpointAuditRecord.fields(
                "myWF", "sapi-billing", "v1", "GET", "/pastDueToday/A0999",
                "/pastDueToday/{agencyCode}", null, caller);

        assertThat(perEndpoint)
                .as("the per-endpoint stream records the entitlements today and must keep not doing so")
                .doesNotContainKey("agencyCodes");

        // The projection reads the claim and nothing about the decision, so it cannot vary with the
        // mode: the same caller produces the same field under enforce and under log-only.
        properties.getSecurity().setAgencyEntitlement(BillingEdgeProperties.Security.Mode.LOG_ONLY);
        Map<String, Object> underLogOnly = new RequestResponseAuditRecord(
                OffsetDateTime.parse("2026-08-17T09:30:00Z"), 1L, "sapi-billing", "v1",
                "corr-1", "interaction-1", "/pastDueToday/A0999", caller).entryFields();

        assertThat(underLogOnly.get("agencyCodes")).isEqualTo(requestResponse.get("agencyCodes"));
    }

    @Test
    @DisplayName("a named, owned and dated exemption is served and counted separately")
    void namedExemptionsAreServedAndCounted() throws Exception {
        // Not a numbered criterion, but it is the escape hatch ADR-0037 provides, and an escape
        // hatch that is never tested is an escape hatch that fails the first time it is needed.
        CallerContext batchClient = new CallerContext("svc", CallerContext.EMPTY, "batch-client",
                CallerContext.EMPTY, CallerContext.EMPTY, false, List.of());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/pastDueToday/A0999");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestFunnel.putCorrelationId(request, "corr-1");
        RequestFunnel.putCaller(request, batchClient);

        TestFilterChain chain = new TestFilterChain(
                TestFilterChain.doNothing(), filterWith(Set.of("batch-client")));
        chain.doFilter(request, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.terminalReached()).isTrue();
        assertThat(meterRegistry.counter("billing.security.agency_entitlement_exempt",
                "clientId", "batch-client").count()).isEqualTo(1);
    }
}
