package com.westfield.api.billing.edge.s5;

import com.westfield.api.billing.edge.config.BillingEdgeProperties;
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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-0038 asserted against the PRODUCTION profile specifically, in a booted container.
 *
 * <p>{@code ConsoleControllerTest} asserts the disabled branch by constructing the controller with a
 * properties object whose {@code enabled} flag is false. That verifies the branch; it does not verify
 * that the production PROFILE sets the flag, that the profile's other required values are present, or
 * that the console mapping is even registered when it is off. Those are the questions ADR-0038 is
 * actually making a claim about, and they need this profile to be loaded.
 */
@SpringBootTest(
        classes = {com.westfield.api.billing.edge.SysBillingApplication.class, S5TestSupport.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                // The values application-prod.yaml expects from the deployment. Supplied directly
                // rather than through the environment-variable names, because those names do not
                // match what the deployment manifests actually set — see S5DeploymentManifestTest.
                "billing.backend.esb.host=https://esb.westfieldgrp.com",
                "billing.backend.was.host=https://wassvc.westfieldgrp.com",
                "billing.backend.sts.host=https://sso.westfieldgrp.com/sts",
                "billing.backend.vault.host=https://vault.westfieldgrp.com",
                "billing.saml.audience=urn:sts:mulesoft:unt:to:saml:prod",
                "billing.truststore.location=file:/etc/sys-billing/truststore/truststore.jks",
                "billing.truststore.password=a-per-environment-password",
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://sso.westfieldgrp.com"
        })
@ActiveProfiles("prod")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("S5 — the booted container under the PRODUCTION profile")
class S5ProdProfileIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    BillingEdgeProperties properties;

    private ResponseEntity<String> get(String path) {
        return rest.exchange("http://localhost:" + port + path, HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class);
    }

    @Test // CON-001-f
    @DisplayName("CON-001-f: the console path answers 404 under the production profile")
    void theConsoleIsNotServedInProduction() {
        ResponseEntity<String> response = get("/console");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isEqualTo("{\"message\":\"Resource not found\"}");
    }

    @Test // CON-001-f — indistinguishability
    @DisplayName("CON-001-f: a disabled console is indistinguishable from an unknown console path")
    void aDisabledConsoleLooksExactlyLikeAnUnknownPath() {
        ResponseEntity<String> disabled = get("/console");
        ResponseEntity<String> unknown = get("/console/whatever");

        assertThat(disabled.getStatusCode()).isEqualTo(unknown.getStatusCode());
        assertThat(disabled.getBody()).isEqualTo(unknown.getBody());
    }

    @Test // CON-001-g
    @DisplayName("CON-001-g: the console path and enablement come from configuration, not from code")
    void consoleExposureIsConfiguration() {
        assertThat(properties.getConsole().isEnabled()).isFalse();
        assertThat(properties.getConsole().getPath()).isEqualTo("/console");
    }

    @Test // CFG-001-c, CFG-001-f
    @DisplayName("CFG-001-c/f: the production profile resolves the production API instance and the 20s WAS timeout")
    void productionResolvesItsOwnValues() {
        assertThat(properties.getApi().getId()).isEqualTo("15710690");
        assertThat(properties.getPasmIdBilling()).isEqualTo("1991");
        assertThat(properties.getBackend().getWas().getResponseTimeout())
                .as("response.timeout=20000 in production only (N-0003 ec 3)")
                .isEqualTo(Duration.ofSeconds(20));
        assertThat(properties.getBackend().getEsb().getResponseTimeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test // SEC-002 / ADR-0037 exit criterion
    @DisplayName("SEC-002: entitlement enforcement is ON in the production profile")
    void entitlementEnforcementIsOnInProduction() {
        assertThat(properties.getSecurity().getAgencyEntitlement())
                .isEqualTo(BillingEdgeProperties.Security.Mode.ENFORCE);
        assertThat(properties.getSecurity().getAgencyEntitlementExemptClients()).isEmpty();
    }

    @Test // DEF-0104 under prod
    @DisplayName("DEF-0104: the actuator probes are 404 under the production profile too")
    void actuatorProbesAre404InProductionAsWell() {
        assertThat(get("/actuator/health/readiness").getStatusCode().value()).isEqualTo(404);
        assertThat(get("/actuator/health/liveness").getStatusCode().value()).isEqualTo(404);
    }
}
