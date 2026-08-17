package com.westfield.api.billing.edge.s5;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The admission funnel, /info and /console against a REAL booted container on a real socket.
 *
 * <p>Every assertion here is an observation. Where the observed behaviour contradicts the packet's
 * acceptance criterion or the frozen contract, the test asserts the OBSERVED value and names the
 * defect in the display name, so the evidence pack has a reproduction to point at rather than a
 * green test that hides the divergence. That is deliberate: an S5 test that asserted the criterion
 * and failed would be reverted by the next build; an S5 test that asserts the observation makes the
 * divergence permanent evidence.
 */
@SpringBootTest(
        classes = {com.westfield.api.billing.edge.SysBillingApplication.class, S5TestSupport.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("local")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("S5 — the booted billing-edge container")
class S5BootedEdgeIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<String> get(String path, HttpHeaders headers) {
        return rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    // -------------------------------------------------------------------------------------------
    // ADM-001 / SEC-001 — the admission funnel and the correlation id.
    // -------------------------------------------------------------------------------------------

    @Test // ADM-001-a, ADM-001-d, INF-001-a
    @DisplayName("ADM-001-a/d: /info with a valid token answers 200 and echoes x-correlation-id")
    void infoAnswers200AndEchoesTheCorrelationId() {
        ResponseEntity<String> response = get("/info", bearer(S5TestSupport.VALID));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst("x-correlation-id")).isNotBlank();
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/json");
        assertThat(response.getBody())
                .contains("buildNumber").contains("buildName").contains("gitCommit")
                .contains("otherBuildInfo").contains("timestamp");
    }

    @Test // INF-001-a, INF-001-b, INF-001-e — DEF-0109 observed on the wire
    @DisplayName("INF-001-a: /info reports the '0'/'--' unavailable defaults for EVERY provenance field (DEF-0109)")
    void infoCannotIdentifyTheBuildItIsRunning() {
        ResponseEntity<String> response = get("/info", bearer(S5TestSupport.VALID));

        // INF-001-b's defaults are correct and reachable; INF-001-a's happy path is not, because the
        // build never produces META-INF/build-info.properties for BuildProperties to be built from.
        assertThat(response.getBody()).contains("\"buildNumber\":\"0\"");
        assertThat(response.getBody()).contains("\"buildName\":\"--\"");
        assertThat(response.getBody()).contains("\"gitCommit\":\"--\"");
        assertThat(response.getBody()).contains("\"otherBuildInfo\":\"--\"");
    }

    @Test // INF-001-e
    @DisplayName("INF-001-e: the /info timestamp carries an explicit timezone offset")
    void infoTimestampCarriesAnExplicitOffset() {
        ResponseEntity<String> response = get("/info", bearer(S5TestSupport.VALID));

        String body = response.getBody();
        int at = body.indexOf("\"timestamp\":\"") + "\"timestamp\":\"".length();
        String timestamp = body.substring(at, body.indexOf('"', at));
        assertThat(java.time.OffsetDateTime.parse(timestamp).getOffset()).isNotNull();
        assertThat(timestamp).matches(".*(Z|[+-]\\d\\d:\\d\\d)$");
    }

    @Test // INF-001-g
    @DisplayName("INF-001-g: /info makes no outbound call — it answers with no STS or vault reachable")
    void infoCallsNoBackend() {
        // The local profile points the STS and the vault at http://localhost:8089, where nothing is
        // listening. /info answering 200 is the evidence that neither was consulted.
        assertThat(get("/info", bearer(S5TestSupport.VALID)).getStatusCode().value()).isEqualTo(200);
    }

    @Test // ADM-001-d
    @DisplayName("ADM-001-d: a rejected request also carries x-correlation-id")
    void aRejectedRequestAlsoCarriesTheCorrelationId() {
        ResponseEntity<String> response = get("/no-such-resource", bearer(S5TestSupport.VALID));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getHeaders().getFirst("x-correlation-id")).isNotBlank();
    }

    @Test // ADM-001-d
    @DisplayName("ADM-001-d: a caller-supplied x-correlation-id is echoed back unchanged")
    void aSuppliedCorrelationIdIsEchoedBack() {
        HttpHeaders headers = bearer(S5TestSupport.VALID);
        headers.set("x-correlation-id", "caller-supplied-1234");

        ResponseEntity<String> response = get("/info", headers);

        assertThat(response.getHeaders().getFirst("x-correlation-id")).isEqualTo("caller-supplied-1234");
    }

    // -------------------------------------------------------------------------------------------
    // ADM-003 — the six contract-violation classes, observed on the wire.
    // -------------------------------------------------------------------------------------------

    @Test // ADM-003-b
    @DisplayName("ADM-003-b: an undeclared path is 404 with the fixed one-field body")
    void undeclaredPathIs404() {
        ResponseEntity<String> response = get("/nope", bearer(S5TestSupport.VALID));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isEqualTo("{\"message\":\"Resource not found\"}");
    }

    @Test // ADM-003-c
    @DisplayName("ADM-003-c: a non-GET verb on a declared resource is 405 with NO Allow header")
    void nonGetVerbIs405WithoutAllowHeader() {
        ResponseEntity<String> response = rest.exchange(url("/info"), HttpMethod.POST,
                new HttpEntity<>(bearer(S5TestSupport.VALID)), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody()).isEqualTo("{\"message\":\"Method not allowed\"}");
        assertThat(response.getHeaders().get(HttpHeaders.ALLOW))
                .as("the legacy sets no Allow header on a 405 (N-0026 ec); Spring adds one by default")
                .isNull();
    }

    @Test // ADM-003-d
    @DisplayName("ADM-003-d: an Accept header that excludes JSON is 406")
    void acceptExcludingJsonIs406() {
        HttpHeaders headers = bearer(S5TestSupport.VALID);
        headers.set(HttpHeaders.ACCEPT, "text/xml");

        ResponseEntity<String> response = get("/info", headers);

        assertThat(response.getStatusCode().value()).isEqualTo(406);
        assertThat(response.getBody()).isEqualTo("{\"message\":\"Not acceptable\"}");
    }

    @Test // ADM-003-e
    @DisplayName("ADM-003-e: a GET carrying a body with an unexpected Content-Type is 415")
    void unexpectedContentTypeIs415() {
        HttpHeaders headers = bearer(S5TestSupport.VALID);
        headers.set(HttpHeaders.CONTENT_TYPE, "text/plain");

        ResponseEntity<String> response = rest.exchange(url("/info"), HttpMethod.GET,
                new HttpEntity<>("a body on a GET", headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(415);
        assertThat(response.getBody()).isEqualTo("{\"message\":\"Unsupported media type\"}");
    }

    // -------------------------------------------------------------------------------------------
    // ADM-002 — parameter and query-parameter admission, observed on the wire.
    // -------------------------------------------------------------------------------------------

    @Test // ADM-002-b
    @DisplayName("ADM-002-b: a billingAccountNumber that is not 10 characters is 400")
    void shortBillingAccountNumberIs400() {
        ResponseEntity<String> response = get(
                "/externalPrimaryAccount/12345/account-billing?environment=TEST", bearer(S5TestSupport.VALID));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isEqualTo("{\"message\":\"Bad request\"}");
    }

    @Test // ADM-002-d
    @DisplayName("ADM-002-d: omitting the mandatory-but-unread environment parameter is 400")
    void omittingEnvironmentIs400() {
        ResponseEntity<String> response = get(
                "/externalPrimaryAccount/1234567890/account-billing", bearer(S5TestSupport.VALID));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isEqualTo("{\"message\":\"Bad request\"}");
    }

    @Test // ADM-002-f
    @DisplayName("ADM-002-f: the three /primaryAccount resources are admitted without environment")
    void primaryAccountResourcesDoNotRequireEnvironment() {
        // Admission must NOT answer 400. There is no implementation behind these resources in this
        // packet (billing-account is an empty scaffold), so what admission passes through reaches the
        // dispatcher and comes back 501 — the declared-but-unimplemented class, which is exactly what
        // ADM-002-h and ADM-003-f describe. The point asserted here is that it is not a 400.
        for (String path : List.of("/primaryAccount", "/primaryAccount/transactions",
                "/primaryAccount/policy/escrow/transactions")) {
            ResponseEntity<String> response = get(path, bearer(S5TestSupport.VALID));
            assertThat(response.getStatusCode().value())
                    .as("%s must not be rejected for a missing environment parameter", path)
                    .isNotEqualTo(400);
        }
    }

    @Test // ADM-002-h, ADM-003-f
    @DisplayName("ADM-002-h: a declared resource with no implementation behind it is 501, not 404")
    void declaredButUnimplementedResourceIs501() {
        // /primaryAccount is declared in ApiResourceTable and billing-account ships no controller for
        // it, so this is the real 501 path rather than a table seam.
        ResponseEntity<String> response = get("/primaryAccount", bearer(S5TestSupport.VALID));

        assertThat(response.getStatusCode().value()).isEqualTo(501);
        assertThat(response.getBody()).isEqualTo("{\"message\":\"Not Implemented\"}");
    }

    // -------------------------------------------------------------------------------------------
    // SEC-001 — the token requirement, observed on the wire. DEF-0101 lives here.
    // -------------------------------------------------------------------------------------------

    @Test // SEC-001-b, ADM-003-a
    @DisplayName("SEC-001-b: /info with no Authorization header is rejected — but with 400, which the frozen contract does not declare (DEF-0102)")
    void infoWithoutAuthorizationIsRejectedWith400NotThe401TheContractDeclares() {
        ResponseEntity<String> response = get("/info", new HttpHeaders());

        // The criterion only asks that it be REJECTED, and it is.
        assertThat(response.getStatusCode().is2xxSuccessful()).isFalse();
        // The observation: 400 with the one-field body. contracts/billing-edge/openapi.yaml declares
        // 200/401/500 for getBuildInfo and documents 401 as "No token, or a token the service
        // rejected". 400 is undeclared. Recorded as DEF-0102 (contract defect).
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isEqualTo("{\"message\":\"Bad request\"}");
    }

    @Test // SEC-001-a
    @DisplayName("SEC-001-a: a present-but-invalid bearer token is 401")
    void invalidBearerTokenIs401() {
        ResponseEntity<String> response = get("/info", bearer(S5TestSupport.INVALID));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test // SEC-001-a — DEF-0101
    @DisplayName("SEC-001-a: a NON-bearer Authorization header reaches the implementation unauthenticated (DEF-0101)")
    void nonBearerAuthorizationHeaderBypassesTokenValidationEntirely() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic YWRtaW46YWRtaW4=");

        ResponseEntity<String> response = get("/info", headers);

        // SecurityConfiguration authorizes anyRequest().permitAll(); the bearer filter only engages
        // when the scheme is Bearer, and InboundAdmissionRule only checks that the header is
        // non-blank. So an arbitrary Authorization value satisfies admission AND skips validation.
        assertThat(response.getStatusCode().value())
                .as("SEC-001-a asserts that a request without a bearer token is rejected and no "
                        + "implementation logic runs. It reached the implementation and answered 200.")
                .isEqualTo(200);
        assertThat(response.getBody()).contains("buildNumber");
    }

    @Test // SEC-001-a — DEF-0101, second shape
    @DisplayName("SEC-001-a: a garbage Authorization value also reaches the implementation (DEF-0101)")
    void garbageAuthorizationValueAlsoReachesTheImplementation() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "not-even-a-scheme");

        ResponseEntity<String> response = get("/info", headers);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    // -------------------------------------------------------------------------------------------
    // SEC-002 — agency entitlement enforcement, observed on the wire.
    // -------------------------------------------------------------------------------------------

    @Test // SEC-002-a
    @DisplayName("SEC-002-a: an unentitled caller is denied 403 on pastDueToday before any routing")
    void unentitledCallerIsDeniedOnPastDueToday() {
        String token = S5TestSupport.tokenWith("s5-unentitled",
                Map.of("sub", "u1", "clientId", "client-x", "agencyCodes", List.of("Z9999")));

        ResponseEntity<String> response =
                get("/pastDueToday/A0421?environment=TEST", bearer(token));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        assertThat(response.getBody()).contains("agency-entitlement");
    }

    @Test // SEC-002-b
    @DisplayName("SEC-002-b: an unentitled caller is denied 403 on pendingCancelToday")
    void unentitledCallerIsDeniedOnPendingCancelToday() {
        String token = S5TestSupport.tokenWith("s5-unentitled-2",
                Map.of("sub", "u1", "clientId", "client-x", "agencyCodes", List.of("Z9999")));

        ResponseEntity<String> response =
                get("/pendingCancelToday/A0421?environment=TEST", bearer(token));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test // SEC-002-d
    @DisplayName("SEC-002-d: an absent agencyCodes claim denies, identically on both worklists")
    void absentAgencyClaimDeniesOnBothWorklists() {
        String token = S5TestSupport.tokenWith("s5-no-claim", Map.of("sub", "u1", "clientId", "client-y"));

        assertThat(get("/pastDueToday/A0421?environment=TEST", bearer(token)).getStatusCode().value())
                .isEqualTo(403);
        assertThat(get("/pendingCancelToday/A0421?environment=TEST", bearer(token)).getStatusCode().value())
                .isEqualTo(403);
    }

    @Test // SEC-002-d
    @DisplayName("SEC-002-d: an EMPTY agencyCodes claim denies, as ADR-0037 requires")
    void emptyAgencyClaimDenies() {
        String token = S5TestSupport.tokenWith("s5-empty-claim",
                Map.of("sub", "u1", "clientId", "client-z", "agencyCodes", List.of()));

        assertThat(get("/pastDueToday/A0421?environment=TEST", bearer(token)).getStatusCode().value())
                .isEqualTo(403);
    }

    @Test // SEC-002 — DEF-0106: claim shape robustness
    @DisplayName("SEC-002: a malformed agencyCodes claim (object, not list) denies rather than failing (fail-closed)")
    void malformedAgencyClaimFailsClosed() {
        String token = S5TestSupport.tokenWith("s5-malformed-claim",
                Map.of("sub", "u1", "clientId", "client-m",
                        "agencyCodes", Map.of("codes", List.of("A0421"))));

        ResponseEntity<String> response = get("/pastDueToday/A0421?environment=TEST", bearer(token));

        assertThat(response.getStatusCode().value())
                .as("a claim the service cannot read must not become an allow")
                .isEqualTo(403);
    }

    @Test // SEC-002 — DEF-0106
    @DisplayName("SEC-002: an entitled caller whose claim differs only in CASE is denied (DEF-0106)")
    void caseDifferingAgencyCodeIsDenied() {
        String token = S5TestSupport.tokenWith("s5-lowercase-claim",
                Map.of("sub", "u1", "clientId", "client-c", "agencyCodes", List.of("a0421")));

        ResponseEntity<String> response = get("/pastDueToday/A0421?environment=TEST", bearer(token));

        assertThat(response.getStatusCode().value())
                .as("AgencyEntitlementRule uses List#contains, an exact byte comparison. Fail-closed, "
                        + "but it denies a caller the legacy served and ADR-0037 does not state a "
                        + "comparison rule.")
                .isEqualTo(403);
    }

    @Test // SEC-002 — DEF-0106
    @DisplayName("SEC-002: an entitled caller whose claim is whitespace-padded is denied (DEF-0106)")
    void whitespacePaddedAgencyCodeIsDenied() {
        String token = S5TestSupport.tokenWith("s5-padded-claim",
                Map.of("sub", "u1", "clientId", "client-w", "agencyCodes", List.of(" A0421 ")));

        assertThat(get("/pastDueToday/A0421?environment=TEST", bearer(token)).getStatusCode().value())
                .isEqualTo(403);
    }

    @Test // SEC-002-a — the entitled path
    @DisplayName("SEC-002: an ENTITLED caller passes the entitlement filter (and then meets the unimplemented endpoint)")
    void entitledCallerPassesTheEntitlementFilter() {
        ResponseEntity<String> response =
                get("/pastDueToday/A0421?environment=TEST", bearer(S5TestSupport.VALID));

        assertThat(response.getStatusCode().value())
                .as("not a 403: the entitlement filter admitted the request")
                .isNotEqualTo(403);
        assertThat(response.getStatusCode().value()).isEqualTo(501);
    }

    // -------------------------------------------------------------------------------------------
    // CON-001 — the documentation console, observed on the wire. DEF-0103 lives here.
    // -------------------------------------------------------------------------------------------

    @Test // CON-001-a — DEF-0103
    @DisplayName("CON-001-a: the console cannot serve the contract from a booted service (DEF-0103)")
    void consoleCannotResolveTheContractFromABootedService() {
        ResponseEntity<String> response = get("/console", new HttpHeaders());

        // billing.console.contract-location defaults to file:contracts/billing-edge/openapi.yaml —
        // a path relative to the PROCESS WORKING DIRECTORY, and the contract is not packaged into the
        // artifact (verified: the jar contains no openapi.yaml). ConsoleController therefore falls to
        // notFound() in every environment where the process is not started from the repository root.
        assertThat(response.getStatusCode().value())
                .as("CON-001-a asserts the API's own published contract is returned as browsable "
                        + "documentation. It is not: the resource does not resolve.")
                .isEqualTo(404);
        assertThat(response.getBody()).isEqualTo("{\"message\":\"Resource not found\"}");
    }

    @Test // CON-001-b
    @DisplayName("CON-001-b: an unrecognised console path answers 404 with the main API's fixed body")
    void unknownConsolePathIs404WithTheFixedBody() {
        ResponseEntity<String> response = get("/console/does-not-exist", new HttpHeaders());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isEqualTo("{\"message\":\"Resource not found\"}");
    }

    @Test // CON-001-d
    @DisplayName("CON-001-d: a console response does NOT echo the correlation id, unlike the main API")
    void consoleDoesNotEchoTheCorrelationId() {
        ResponseEntity<String> console = get("/console", new HttpHeaders());
        ResponseEntity<String> main = get("/info", bearer(S5TestSupport.VALID));

        assertThat(console.getHeaders().getFirst("x-correlation-id")).isNull();
        assertThat(main.getHeaders().getFirst("x-correlation-id")).isNotBlank();
    }

    @Test // CON-001-a — the console requires no token
    @DisplayName("CON-001: the console is reachable with no Authorization header, as today")
    void consoleRequiresNoToken() {
        ResponseEntity<String> response = get("/console", new HttpHeaders());

        assertThat(response.getStatusCode().value())
                .as("not 400: admission does not apply to the console path")
                .isNotEqualTo(400);
    }

    // -------------------------------------------------------------------------------------------
    // ADM-004-c — the externally visible base path. DEF-0111 lives here.
    // -------------------------------------------------------------------------------------------

    @Test // ADM-004-c — DEF-0111
    @DisplayName("ADM-004-c: billing.api.base-path is validated, logged, and applied to nothing (DEF-0111)")
    void theConfiguredBasePathIsNeverApplied() {
        // application-local.yaml sets billing.api.base-path: /sapi-billing/v1, reproducing the legacy
        // local listener path. StartupConfigurationValidator requires it and prints it. Nothing binds
        // it to server.servlet.context-path or to any mapping, so the service serves at the root in
        // every profile and the configured value is inert.
        assertThat(get("/info", bearer(S5TestSupport.VALID)).getStatusCode().value())
                .as("served at the root even though the profile declares /sapi-billing/v1")
                .isEqualTo(200);
        assertThat(get("/sapi-billing/v1/info", bearer(S5TestSupport.VALID)).getStatusCode().value())
                .as("ADM-004-c asserts the externally visible base path RESOLVES to the deployment's "
                        + "value. It does not resolve to anything: the declared path is not served.")
                .isEqualTo(404);
    }

    // -------------------------------------------------------------------------------------------
    // Operational surface. DEF-0104 lives here.
    // -------------------------------------------------------------------------------------------

    @Test // DEF-0104
    @DisplayName("the actuator health probes named by kustomize/base/deployment.yaml answer 404 (DEF-0104)")
    void actuatorHealthProbesAreSwallowedByTheAdmissionFilter() {
        // kustomize/base/deployment.yaml declares readinessProbe and livenessProbe against
        // /actuator/health/readiness and /actuator/health/liveness on port 8081. InboundValidationFilter
        // rejects every path absent from ApiResourceTable with the routing 404 before the dispatcher
        // ever sees it, so both probes fail permanently and the pod never becomes ready.
        for (String probe : List.of("/actuator/health/readiness", "/actuator/health/liveness",
                "/actuator/health")) {
            ResponseEntity<String> response = get(probe, new HttpHeaders());
            assertThat(response.getStatusCode().value())
                    .as("%s is the probe path the deployment manifest uses", probe)
                    .isEqualTo(404);
            assertThat(response.getBody()).isEqualTo("{\"message\":\"Resource not found\"}");
        }
    }
}
