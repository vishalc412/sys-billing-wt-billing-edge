package com.westfield.api.billing.edge.s5;

import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.edge.config.StartupConfigurationValidator;
import com.westfield.api.billing.edge.domain.security.AgencyEntitlementDecision;
import com.westfield.api.billing.edge.domain.security.AgencyEntitlementRule;
import com.westfield.api.billing.edge.testsupport.Fixtures;
import com.westfield.api.billing.platform.spi.CallerContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Three probes that the S4 suite could not have caught, because each of them is about something
 * OUTSIDE the class under test: the build, the deployment manifests, and the gap between what a
 * configuration value says and what the code does with it.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("S5 — build provenance, exemption expiry and deployment wiring")
class S5ProvenanceAndExemptionProbeTest {

    // ---------------------------------------------------------------------------------------------
    // DEF-0108 — the exemption expiry is collected, validated for presence, and then discarded.
    // ---------------------------------------------------------------------------------------------

    @Test // SEC-002, ADR-0037 — DEF-0108
    @DisplayName("DEF-0108: an exemption that expired years ago still grants access")
    void anExpiredExemptionStillGrantsAccess() {
        BillingEdgeProperties properties = Fixtures.validProperties();
        properties.getSecurity().getAgencyEntitlementExemptClients().add(
                Fixtures.exemption("legacy-batch-client", "someone-who-left", LocalDate.of(2020, 1, 1)));

        // Startup accepts it: the check is "an expiry is PRESENT", never "an expiry is in the future".
        new StartupConfigurationValidator(properties).validate(List.of("test"));

        // BillingEdgeConfiguration#agencyEntitlementRule projects the list down to client ids and
        // throws the owner and the expiry away, so the rule cannot honour an expiry even in principle.
        AgencyEntitlementRule rule = new AgencyEntitlementRule(Set.of("legacy-batch-client"));
        CallerContext expiredExemptCaller = new CallerContext(
                "u1", "u1@westfieldgrp.com", "legacy-batch-client",
                CallerContext.EMPTY, CallerContext.EMPTY, false, List.of());

        assertThat(rule.decide("A0421", expiredExemptCaller, true))
                .as("ADR-0037 requires an exemption to carry an owner AND AN EXPIRY. The expiry is "
                        + "required at startup and then never consulted, so an exemption never expires "
                        + "and a caller with no agency entitlement at all keeps cross-agency access to "
                        + "customer PII indefinitely.")
                .isEqualTo(AgencyEntitlementDecision.EXEMPT);
    }

    @Test // SEC-002, ADR-0037
    @DisplayName("an exemption beats an EMPTY agency claim, so exemption is a full bypass not a widening")
    void anExemptionBypassesTheClaimEntirely() {
        AgencyEntitlementRule rule = new AgencyEntitlementRule(Set.of("client-exempt"));
        CallerContext noEntitlements = new CallerContext("u1", "u1@x.com", "client-exempt",
                CallerContext.EMPTY, CallerContext.EMPTY, false, List.of());

        assertThat(rule.decide("ANY-AGENCY-AT-ALL", noEntitlements, true))
                .isEqualTo(AgencyEntitlementDecision.EXEMPT);
    }

    @Test // SEC-002-e
    @DisplayName("SEC-002-e: log-only mode serves the unentitled request rather than denying it")
    void logOnlyModeServesTheUnentitledRequest() {
        AgencyEntitlementRule rule = new AgencyEntitlementRule(Set.of());
        CallerContext unentitled = new CallerContext("u1", "u1@x.com", "client-a",
                CallerContext.EMPTY, CallerContext.EMPTY, false, List.of("Z9999"));

        assertThat(rule.decide("A0421", unentitled, false))
                .isEqualTo(AgencyEntitlementDecision.UNENTITLED_SERVED);
        assertThat(rule.decide("A0421", unentitled, true))
                .isEqualTo(AgencyEntitlementDecision.DENIED);
    }

    @Test // SEC-002-d — the null-caller path
    @DisplayName("SEC-002-d: a request with no decoded token at all is denied when enforcing")
    void aRequestWithNoTokenContextIsDenied() {
        AgencyEntitlementRule rule = new AgencyEntitlementRule(Set.of());

        assertThat(rule.decide("A0421", null, true)).isEqualTo(AgencyEntitlementDecision.DENIED);
    }

    // ---------------------------------------------------------------------------------------------
    // DEF-0109 — /info cannot identify the build in a packaged artifact.
    // ---------------------------------------------------------------------------------------------

    @Test // INF-001-a, INF-001-c — DEF-0109
    @DisplayName("DEF-0109: the packaged jar carries no build-info.properties, so /info can only report defaults")
    void thePackagedArtifactCarriesNoBuildMetadata() throws Exception {
        Path jar = Path.of("target/billing-edge-0.1.0-SNAPSHOT.jar");
        if (!Files.exists(jar)) {
            // The jar only exists after `package`. Assert the cause instead, which is always present.
            assertThat(Files.readString(Path.of("pom.xml")))
                    .as("spring-boot-maven-plugin declares no build-info goal, so "
                            + "META-INF/build-info.properties is never generated and BuildProperties "
                            + "is never auto-configured")
                    .doesNotContain("build-info");
            return;
        }
        try (JarFile packaged = new JarFile(jar.toFile())) {
            // The observation is asserted, not the criterion: INF-001-a asserts /info carries the
            // build number, build name, commit hash and remaining build metadata. Without
            // build-info.properties the BuildProperties bean is never auto-configured and every one
            // of those fields falls to its '0'/'--' unavailable default, permanently and silently —
            // which is the same class of failure as the legacy's unsubstituted @pomBuildNumber@ that
            // ADR-0018 set out to remove.
            assertThat(packaged.stream().map(java.util.jar.JarEntry::getName)
                    .anyMatch(name -> name.endsWith("build-info.properties")))
                    .as("no build-info.properties in the packaged artifact — DEF-0109")
                    .isFalse();
        }
        assertThat(Files.readString(Path.of("pom.xml")))
                .as("and the cause: spring-boot-maven-plugin declares no build-info goal")
                .doesNotContain("build-info");
    }

    @Test // INF-001-c — DEF-0109, second half
    @DisplayName("DEF-0109: nothing fails the BUILD when an unsubstituted token would reach the artifact")
    void noBuildStepEnforcesTokenSubstitution() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertThat(pom)
                .as("INF-001-c says 'the build fails if any such token reaches the artifact'. The only "
                        + "check that exists is StartupValidationRunner, which runs at BOOT, and it can "
                        + "never fire because no build metadata is produced for it to inspect.")
                .doesNotContain("enforcer").doesNotContain("build-info");
    }

    // ---------------------------------------------------------------------------------------------
    // DEF-0110 — the deployment manifests set environment variables the application never reads.
    // ---------------------------------------------------------------------------------------------

    @Test // CFG-001-c, CFG-001-d — DEF-0110
    @DisplayName("DEF-0110: every ${...} placeholder the prod profile needs is unset by the prod deployment manifest")
    void theDeploymentManifestsDoNotSupplyThePropertiesTheProfilesRead() throws Exception {
        Path repoRoot = Path.of("../..").toRealPath();
        String prodProfile = Files.readString(
                Path.of("src/main/resources/application-prod.yaml"));

        StringBuilder manifests = new StringBuilder();
        try (Stream<Path> files = Files.walk(repoRoot.resolve("kustomize"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".yaml")).toList()) {
                manifests.append(Files.readString(file)).append('\n');
            }
        }

        Matcher placeholders = Pattern.compile("\\$\\{([A-Z0-9_]+)[:}]").matcher(prodProfile);
        java.util.List<String> unsupplied = new java.util.ArrayList<>();
        while (placeholders.find()) {
            String variable = placeholders.group(1);
            if (!manifests.toString().contains("name: " + variable)) {
                unsupplied.add(variable);
            }
        }

        assertThat(unsupplied)
                .as("kustomize/ sets PRIMARY_ACCOUNT_HOST, EXTERNAL_PRIMARY_ACCOUNT_HOST, PING_FED_HOST, "
                        + "TRUSTSTORE_PASSWORD and OAUTH_ISSUER_URI — none of which any profile reads. "
                        + "application-prod.yaml reads the BILLING_* names below and none is set by any "
                        + "manifest, so a deployment built from kustomize/ cannot start.")
                .containsExactlyInAnyOrder("BILLING_BASE_PATH", "BILLING_SAML_AUDIENCE",
                        "BILLING_ESB_HOST", "BILLING_WAS_HOST", "BILLING_STS_HOST", "BILLING_VAULT_HOST",
                        "BILLING_TRUSTSTORE_LOCATION", "BILLING_TRUSTSTORE_PASSWORD",
                        "BILLING_JWT_ISSUER_URI");
    }
}
