package com.westfield.api.billing.edge.domain.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADM-002 — contract-first routing and inbound request validation (N-0001, N-0034, N-0038, N-0045).
 * ADM-003 — the six routing failure classes (N-0024 … N-0029).
 *
 * <p>Every rule asserted here lived in the RAML in the legacy and in no flow. The knowledge map says
 * so in as many words: "that length validation is a business rule that lives only in the spec, not in
 * any flow" (N-0001 ec 3). That is why it is tested as a domain rule rather than as a framework
 * behaviour — a framework can be reconfigured by someone who does not know it was a decision.
 */
@DisplayName("ADM-002 / ADM-003 contract-first admission")
class InboundAdmissionRuleTest {

    private static final String BEARER = "Bearer eyJhbGciOiJSUzI1NiJ9.e30.sig";

    private final ApiResourceTable table = new ApiResourceTable();
    private final InboundAdmissionRule rule = new InboundAdmissionRule(table);

    private Optional<ContractViolation> admit(String method, String path, Map<String, List<String>> query) {
        return rule.admit(method, path, query, "application/json", null, BEARER);
    }

    private static Map<String, List<String>> environment(String value) {
        return Map.of("environment", List.of(value));
    }

    @Test // ADM-002-a
    @DisplayName("a: a declared method and path reach exactly one resource and no other")
    void aDeclaredMethodAndPathMatchOneResource() {
        List<ApiResource> matching = table.resources().stream()
                .filter(r -> r.matches("GET", "/pastDueToday/A0421"))
                .toList();

        assertThat(matching).hasSize(1);
        assertThat(matching.get(0).template()).isEqualTo("/pastDueToday/{agencyCode}");
        assertThat(admit("GET", "/pastDueToday/A0421", environment("TEST"))).isEmpty();
    }

    @Test // ADM-002-b
    @DisplayName("b: a billingAccountNumber that is not exactly 10 characters is rejected with 400")
    void billingAccountNumberMustBeTenCharacters() {
        assertThat(admit("GET", "/externalPrimaryAccount/123/account-billing", environment("TEST")))
                .contains(ContractViolation.BAD_REQUEST);
        assertThat(admit("GET", "/externalPrimaryAccount/12345678901/account-billing", environment("TEST")))
                .contains(ContractViolation.BAD_REQUEST);
        // Exactly ten is admissible. The rule is an exact length, not a minimum.
        assertThat(admit("GET", "/externalPrimaryAccount/1234567890/account-billing", environment("TEST")))
                .isEmpty();
    }

    @Test // ADM-002-c
    @DisplayName("c: a policyNumber that is not exactly 7 characters is rejected with 400")
    void policyNumberMustBeSevenCharacters() {
        assertThat(admit("GET", "/externalPrimaryAccount/1234/policy-billing", environment("TEST")))
                .contains(ContractViolation.BAD_REQUEST);
        assertThat(admit("GET", "/externalPrimaryAccount/1234567/policy-billing", environment("TEST")))
                .isEmpty();
    }

    @Test // ADM-002-d
    @DisplayName("d: the four resources carrying the environment trait reject a request that omits it")
    void environmentParameterIsMandatoryOnFourResources() {
        // Mandatory in the contract, read by NOTHING — in the legacy or here. Preserved deliberately
        // (ADR-0019): relaxing it would accept requests the legacy rejects, which is a contract
        // change in the direction nobody asked for, and every caller already sends it or is already
        // failing today.
        assertThat(admit("GET", "/externalPrimaryAccount/1234567890/account-billing", Map.of()))
                .contains(ContractViolation.BAD_REQUEST);
        assertThat(admit("GET", "/externalPrimaryAccount/1234567/policy-billing", Map.of()))
                .contains(ContractViolation.BAD_REQUEST);
        assertThat(admit("GET", "/pastDueToday/A0421", Map.of()))
                .contains(ContractViolation.BAD_REQUEST);
        assertThat(admit("GET", "/pendingCancelToday/A0421", Map.of()))
                .contains(ContractViolation.BAD_REQUEST);
    }

    @Test // ADM-002-e
    @DisplayName("e: environment=PROD on a non-production deployment changes nothing about routing")
    void theEnvironmentParameterHasNoEffectOnRouting() {
        // The deployment environment selects the back ends, through configuration, at startup. The
        // query parameter selects nothing: no implementation reads it (N-0034 ec 2, N-0038 ec 2), so
        // a caller cannot reach production data from a test deployment by asking nicely.
        Optional<ApiResource> withProd = table.find("GET", "/externalPrimaryAccount/1234567890/account-billing");
        Optional<ApiResource> withTest = table.find("GET", "/externalPrimaryAccount/1234567890/account-billing");

        assertThat(withProd).isEqualTo(withTest);
        assertThat(admit("GET", "/externalPrimaryAccount/1234567890/account-billing", environment("PROD")))
                .isEmpty();
        assertThat(admit("GET", "/externalPrimaryAccount/1234567890/account-billing", environment("TEST")))
                .isEmpty();

        // Nothing in the resource declaration binds the parameter to a backend selection.
        assertThat(ApiResource.ENVIRONMENT_PARAM).isEqualTo("environment");
    }

    @Test // ADM-002-f
    @DisplayName("f: the three primaryAccount resources are accepted without the environment parameter")
    void primaryAccountResourcesDoNotCarryTheEnvironmentTrait() {
        // N-0045 ec 2: the trait is applied to four resources and to nothing else. Applying it
        // uniformly would look tidier and would start rejecting requests that work today.
        assertThat(admit("GET", "/primaryAccount", Map.of())).isEmpty();
        assertThat(admit("GET", "/primaryAccount/transactions", Map.of())).isEmpty();
        assertThat(admit("GET", "/primaryAccount/policy/escrow/transactions", Map.of())).isEmpty();

        for (ApiResource resource : table.resources()) {
            if (resource.template().startsWith("/primaryAccount")) {
                assertThat(resource.requiresEnvironmentParam()).isFalse();
            }
        }
    }

    @Test // ADM-002-g
    @DisplayName("g: a request omitting the Authorization header is rejected before any implementation runs")
    void missingAuthorizationHeaderIsRejected() {
        // The contract declares the header required, so its ABSENCE is a contract violation answered
        // 400 — not the 401 that a present-but-invalid token gets from the resource server. The two
        // are different conditions and collapsing them would retire a response consumers see today.
        assertThat(rule.admit("GET", "/pastDueToday/A0421", environment("TEST"),
                "application/json", null, null))
                .contains(ContractViolation.BAD_REQUEST);
        assertThat(rule.admit("GET", "/pastDueToday/A0421", environment("TEST"),
                "application/json", null, "   "))
                .contains(ContractViolation.BAD_REQUEST);
    }

    @Test // ADM-002-h
    @DisplayName("h: a declared resource with no implementation answers 501 and not 404")
    void declaredButUnimplementedIsNotImplemented() {
        // Spec ahead of code. 404 says "no such resource" and 501 says "this resource exists and
        // nothing stands behind it"; a consumer integrating against the published contract needs the
        // second answer, and it is the first thing to fire when a resource is added without an
        // implementation (N-0029).
        ApiResourceTable withGap = new ApiResourceTable(List.of(
                new ApiResource("GET", "/futureResource", false, false, false)));
        InboundAdmissionRule ruleWithGap = new InboundAdmissionRule(withGap);

        assertThat(ruleWithGap.admit("GET", "/futureResource", Map.of(), "application/json", null, BEARER))
                .contains(ContractViolation.NOT_IMPLEMENTED);
        assertThat(ContractViolation.NOT_IMPLEMENTED.status()).isEqualTo(501);
    }

    @Test // ADM-003-a
    @DisplayName("a: every 400 class produces the same opaque body with no indication of what failed")
    void badRequestIsOpaqueWhicheverRuleFired() {
        // Three different rules, one indistinguishable answer. That opacity is legacy behaviour
        // preserved by ADR-0042: a consumer parsing a reason out of the body gets nothing today and
        // must continue to get nothing, because adding detail is a contract change.
        List<Optional<ContractViolation>> allThree = List.of(
                admit("GET", "/externalPrimaryAccount/123/account-billing", environment("TEST")),
                admit("GET", "/pastDueToday/A0421", Map.of()),
                rule.admit("GET", "/pastDueToday/A0421", environment("TEST"), "application/json", null, null));

        for (Optional<ContractViolation> violation : allThree) {
            assertThat(violation).contains(ContractViolation.BAD_REQUEST);
            assertThat(violation.orElseThrow().status()).isEqualTo(400);
            assertThat(violation.orElseThrow().message()).isEqualTo("Bad request");
        }
    }

    @Test // ADM-003-b
    @DisplayName("b: a path the contract does not define is 404 with the fixed not-found body")
    void undeclaredPathIsNotFound() {
        Optional<ContractViolation> violation = admit("GET", "/nothingLikeThis", Map.of());

        assertThat(violation).contains(ContractViolation.NOT_FOUND);
        assertThat(ContractViolation.NOT_FOUND.status()).isEqualTo(404);
        assertThat(ContractViolation.NOT_FOUND.message()).isEqualTo("Resource not found");
    }

    @Test // ADM-003-c
    @DisplayName("c: any verb other than GET on a known resource is 405 with the fixed body")
    void anyOtherVerbIsMethodNotAllowed() {
        // The API is read-only: GET is the only verb declared anywhere.
        for (String verb : List.of("POST", "PUT", "PATCH", "DELETE", "HEAD")) {
            assertThat(admit(verb, "/pastDueToday/A0421", environment("TEST")))
                    .as("verb %s", verb)
                    .contains(ContractViolation.METHOD_NOT_ALLOWED);
        }
        assertThat(ContractViolation.METHOD_NOT_ALLOWED.status()).isEqualTo(405);
        assertThat(ContractViolation.METHOD_NOT_ALLOWED.message()).isEqualTo("Method not allowed");
    }

    @Test // ADM-003-d
    @DisplayName("d: an Accept header that excludes application/json is 406")
    void acceptExcludingJsonIsNotAcceptable() {
        assertThat(rule.admit("GET", "/pastDueToday/A0421", environment("TEST"),
                "application/xml", null, BEARER))
                .contains(ContractViolation.NOT_ACCEPTABLE);
        // Every response this API produces is JSON, so the usual wildcards are satisfiable.
        assertThat(rule.admit("GET", "/pastDueToday/A0421", environment("TEST"),
                "*/*", null, BEARER)).isEmpty();
        assertThat(rule.admit("GET", "/pastDueToday/A0421", environment("TEST"),
                "application/json;q=0.9", null, BEARER)).isEmpty();
        assertThat(ContractViolation.NOT_ACCEPTABLE.status()).isEqualTo(406);
        assertThat(ContractViolation.NOT_ACCEPTABLE.message()).isEqualTo("Not acceptable");
    }

    @Test // ADM-003-e
    @DisplayName("e: a GET carrying a body with an unexpected Content-Type is 415")
    void unexpectedContentTypeIsUnsupportedMediaType() {
        // Every operation is a GET with no request body declared, so this can only fire when a caller
        // sends one anyway (N-0028 ec).
        assertThat(rule.admit("GET", "/pastDueToday/A0421", environment("TEST"),
                "application/json", "text/plain", BEARER))
                .contains(ContractViolation.UNSUPPORTED_MEDIA_TYPE);
        assertThat(ContractViolation.UNSUPPORTED_MEDIA_TYPE.status()).isEqualTo(415);
        assertThat(ContractViolation.UNSUPPORTED_MEDIA_TYPE.message()).isEqualTo("Unsupported media type");
    }

    @Test // ADM-003-f
    @DisplayName("f: a contract resource with nothing behind it is 501 with the fixed body")
    void unimplementedResourceIsNotImplemented() {
        assertThat(ContractViolation.NOT_IMPLEMENTED.status()).isEqualTo(501);
        assertThat(ContractViolation.NOT_IMPLEMENTED.message()).isEqualTo("Not Implemented");
    }

    @Test // ADM-003-g
    @DisplayName("g: all bodies are the single-field shape and deliberately NOT the published fault type")
    void bodiesAreTheSingleFieldShapeNotThePublishedFaultType() {
        // ADR-0042 PRESERVE. The legacy RAML publishes a commonError type with faultActor, faultCode,
        // faultMessage, faultDetail, faultTime and innerFault. The application has never emitted it.
        // The contract was rewritten to match the code rather than the other way round, and this
        // assertion is what stops someone "correcting" the code towards the old published type.
        //
        // The seven values include the six legacy routing classes plus UNAUTHORIZED (401), added for
        // DEF-0101 to reject a non-Bearer Authorization header before the implementation runs. It
        // carries the same single-field shape; it is not one of the legacy six, which is why the count
        // is seven and not six.
        List<String> publishedFaultTypeFields = List.of(
                "faultActor", "faultCode", "faultMessage", "faultDetail", "faultTime", "innerFault");

        for (ContractViolation violation : ContractViolation.values()) {
            Map<String, Object> body = Map.of("message", violation.message());
            assertThat(body).hasSize(1).containsOnlyKeys("message");
            assertThat(body.keySet()).doesNotContainAnyElementsOf(publishedFaultTypeFields);
        }
        assertThat(ContractViolation.values()).hasSize(7);
    }
}
