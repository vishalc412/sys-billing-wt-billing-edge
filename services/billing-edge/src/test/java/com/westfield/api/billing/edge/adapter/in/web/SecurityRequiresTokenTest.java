package com.westfield.api.billing.edge.adapter.in.web;

import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.edge.domain.api.ApiResource;
import com.westfield.api.billing.edge.domain.api.ApiResourceTable;
import com.westfield.api.billing.edge.domain.api.ContractViolation;
import com.westfield.api.billing.edge.domain.api.InboundAdmissionRule;
import com.westfield.api.billing.edge.domain.audit.RequestResponseAuditRecord;
import com.westfield.api.billing.edge.testsupport.Fixtures;
import com.westfield.api.billing.platform.spi.CallerContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-001 — the bearer token requirement and the claims it makes available (N-0004, N-0021, N-0035,
 * N-0043, ADR-0013).
 *
 * <p><b>The legacy has no authentication of its own.</b> It relies entirely on an API Manager policy
 * whose configuration was never exported (R-003), while its own listener binds {@code 0.0.0.0} over
 * plain HTTP (N-0013 ec 2). Anything that can reach the pod today is unauthenticated. ADR-0013 closes
 * that by making the service an OAuth2 resource server that validates the token itself and never
 * trusts an injected header.
 *
 * <p>Because R-003 was never answered, nothing is invented on top: no scope check, no client-id
 * allowlist, no audience rule beyond what the resource server performs. The claim NAMES below are the
 * ones the legacy expressions demonstrably read and nothing more. Inventing a scope would be
 * indistinguishable, in code, from a scope the legacy actually enforced — and would fail requests
 * that work today.
 *
 * <p>The missing-header case is deliberately a 400 rather than a 401. Two different conditions
 * produce two different legacy responses: the RAML declares the Authorization header required, so
 * APIkit rejects its absence as a CONTRACT violation with the one-field body, while a present-but-bad
 * token is rejected by the policy. Collapsing them into 401 would silently retire the 400 that
 * consumers see today, which is why authorization is {@code permitAll} and the header requirement
 * lives in the admission rule instead.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("SEC-001 bearer token requirement and decoded claims")
class SecurityRequiresTokenTest {

    private final InboundAdmissionRule rule = new InboundAdmissionRule(new ApiResourceTable());
    private final CallerContextFactory callerContexts = new CallerContextFactory();

    /** One well-formed request per declared resource, with the query parameters each one requires. */
    private static Map<String, List<String>> queryFor(ApiResource resource) {
        return resource.requiresEnvironmentParam()
                ? Map.of(ApiResource.ENVIRONMENT_PARAM, List.of("TEST"))
                : Map.of();
    }

    private static String samplePathFor(ApiResource resource) {
        return resource.template()
                .replace("{billingAccountNumber}", "1234567890")
                .replace("{policyNumber}", "1234567")
                .replace("{agencyCode}", "A0421");
    }

    @Test // SEC-001-a
    @DisplayName("a: not one of the eight operations is reachable without a bearer token")
    void noOperationIsReachableWithoutAToken() {
        // Asserted across the WHOLE declared surface rather than on a sample. A gap in authentication
        // is per-resource by nature: it is one endpoint someone forgot, and a spot check is exactly
        // what fails to find it.
        for (ApiResource resource : new ApiResourceTable().resources()) {
            String path = samplePathFor(resource);

            Optional<ContractViolation> withoutToken =
                    rule.admit("GET", path, queryFor(resource), "application/json", null, null);

            assertThat(withoutToken)
                    .as("%s must be rejected before any implementation logic runs", resource.template())
                    .isPresent();

            Optional<ContractViolation> withToken =
                    rule.admit("GET", path, queryFor(resource), "application/json", null, "Bearer t");

            assertThat(withToken)
                    .as("%s must be admissible once a token is present", resource.template())
                    .isEmpty();
        }
    }

    @Test // SEC-001-b
    @DisplayName("b: /info is secured too, so it cannot be used as an unauthenticated health probe")
    void infoIsSecuredLikeEveryOtherResource() {
        // N-0043 ec 1. The legacy covers /info with the same 'secured' trait as every other
        // resource, and it is preserved rather than relaxed: /info publishes the source commit and
        // the build identity, which is reconnaissance material, and the listener is reachable
        // without TLS. Liveness and readiness are the actuator's job.
        assertThat(rule.admit("GET", "/info", Map.of(), "application/json", null, null))
                .contains(ContractViolation.BAD_REQUEST);
        assertThat(rule.admit("GET", "/info", Map.of(), "application/json", null, "Bearer t"))
                .isEmpty();
    }

    @Test // SEC-001-c
    @DisplayName("c: a valid token makes subject, email, client id, actor claims and agency codes available to the audit projection")
    void decodedClaimsReachTheAuditProjection() {
        Jwt jwt = Fixtures.jwt(Map.of(
                "sub", "u123456",
                "email", "adjuster@westfieldgrp.com",
                "clientId", "client-abc",
                "actSub", "u999999",
                "actEmail", "supervisor@westfieldgrp.com",
                "agencyCodes", List.of("A0421", "A0999")));

        CallerContext caller = callerContexts.from(jwt);

        assertThat(caller.subject()).isEqualTo("u123456");
        assertThat(caller.email()).isEqualTo("adjuster@westfieldgrp.com");
        assertThat(caller.clientId()).isEqualTo("client-abc");
        assertThat(caller.actorSubject()).isEqualTo("u999999");
        assertThat(caller.actorEmail()).isEqualTo("supervisor@westfieldgrp.com");
        assertThat(caller.agencyCodes()).containsExactly("A0421", "A0999");

        // And they arrive on the audit record, which is the only reason the API reads them at all.
        Map<String, Object> fields = new RequestResponseAuditRecord(
                OffsetDateTime.parse("2026-08-17T09:30:00Z"), 1L, "sapi-billing", "v1",
                "corr-1", "interaction-1", "/pastDueToday/A0421", caller).entryFields();

        assertThat(fields)
                .containsEntry("sub", "u123456")
                .containsEntry("email", "adjuster@westfieldgrp.com")
                .containsEntry("clientId", "client-abc")
                .containsEntry("actSub", "u999999")
                .containsEntry("agencyCodes", List.of("A0421", "A0999"));

        // The claims come from the TOKEN, never from a header. A service that trusts headers is
        // bypassable the moment it is directly reachable — and this one is (N-0013 ec 2).
        assertThat(callerContexts.fromSecurityContext())
                .as("with no authenticated context there is no caller, whatever headers were sent")
                .isNull();
    }

    @Test // SEC-001-d
    @DisplayName("d: with no decoded token context the client id is null while every neighbour says EMPTY")
    void clientIdIsNullWhileNeighboursSayEmpty() {
        // N-0051 ec 1, preserved deliberately. clientId is the ONE field the legacy leaves without a
        // default, so when the token-enforcement policy has not populated the authentication object
        // it is null while every field beside it says EMPTY. It looks like an oversight and it is —
        // but it is also the only usable detector for "the token policy did not run at all", and log
        // analytics may already key on it. Normalising it to EMPTY would remove that signal silently.
        Map<String, Object> fields = new RequestResponseAuditRecord(
                OffsetDateTime.parse("2026-08-17T09:30:00Z"), 1L, "sapi-billing", "v1",
                "corr-1", "interaction-1", "/info", null).entryFields();

        assertThat(fields).containsEntry("clientId", null);
        assertThat(fields)
                .containsEntry("sub", CallerContext.EMPTY)
                .containsEntry("email", CallerContext.EMPTY)
                .containsEntry("actSub", CallerContext.EMPTY)
                .containsEntry("actEmail", CallerContext.EMPTY);
        assertThat(fields)
                .as("an absent agency-code list is an EMPTY LIST, never the EMPTY sentinel (N-0051 rule 5)")
                .containsEntry("agencyCodes", List.of());
        assertThat(fields)
                .as("the key must be present and null, not omitted")
                .containsKey("clientId");
    }

    @Test // SEC-001-e
    @DisplayName("e: the API identity binding is per environment and is never shared or hardcoded")
    void apiIdentityIsPerEnvironment() {
        // N-0004 ec 1: the legacy registers a different API Manager instance id in each environment,
        // and it is what binds a deployment to its own policy set. A shared or hardcoded id would
        // apply one environment's policies to another's traffic.
        BillingEdgeProperties properties = Fixtures.validProperties();
        assertThat(properties.getApi().getId()).isNotBlank();

        // Nothing may hardcode it: it is a bound property with no default, so an unset value fails
        // the start (asserted in StartupConfigurationValidatorTest CFG-001-d) rather than silently
        // resolving to some other environment's identity. The per-environment VALUES are asserted
        // against the profile files in EnvironmentProfileTest CFG-001-c.
        assertThat(new BillingEdgeProperties().getApi().getId())
                .as("there is no built-in default API instance id")
                .isNull();
    }
}
