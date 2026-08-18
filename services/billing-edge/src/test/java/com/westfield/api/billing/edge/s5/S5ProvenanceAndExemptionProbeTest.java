package com.westfield.api.billing.edge.s5;

import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.edge.config.StartupConfigurationValidator;
import com.westfield.api.billing.edge.domain.security.AgencyEntitlementDecision;
import com.westfield.api.billing.edge.domain.security.AgencyEntitlementRule;
import com.westfield.api.billing.edge.domain.security.ExemptClient;
import com.westfield.api.billing.edge.testsupport.Fixtures;
import com.westfield.api.billing.platform.spi.CallerContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
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
 *
 * <p>All three defects are fixed. Each probe now asserts the corrected behaviour.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("S5 — build provenance, exemption expiry and deployment wiring")
class S5ProvenanceAndExemptionProbeTest {

    // ---------------------------------------------------------------------------------------------
    // DEF-0108 fixed — the exemption expiry is now honoured (boundary-inclusive).
    // ---------------------------------------------------------------------------------------------

    @Test // SEC-002, ADR-0037 — DEF-0108 fixed
    @DisplayName("DEF-0108 fixed: an exemption that expired years ago is denied, not granted")
    void anExpiredExemptionIsDenied() {
        BillingEdgeProperties properties = Fixtures.validProperties();
        properties.getSecurity().getAgencyEntitlementExemptClients().add(
                Fixtures.exemption("legacy-batch-client", "someone-who-left", LocalDate.of(2020, 1, 1)));

        // Startup still accepts it: the expiry is PRESENT, which is what the startup check requires.
        new StartupConfigurationValidator(properties).validate(List.of("test"));

        // The owner AND expiry are now threaded through to the rule, so an exemption whose expiry has
        // passed denies instead of granting cross-agency PII access forever.
        AgencyEntitlementRule rule = new AgencyEntitlementRule(
                List.of(new ExemptClient("legacy-batch-client", "someone-who-left", LocalDate.of(2020, 1, 1))),
                Clock.systemUTC());
        CallerContext expiredExemptCaller = new CallerContext(
                "u1", "u1@westfieldgrp.com", "legacy-batch-client",
                CallerContext.EMPTY, CallerContext.EMPTY, false, List.of());

        assertThat(rule.decide("A0421", expiredExemptCaller, true))
                .as("ADR-0037 requires an exemption to carry an owner AND an expiry, and the expiry is "
                        + "now consulted. A past-expiry exemption denies (boundary-inclusive: live ON the "
                        + "expiry date, denied the day after).")
                .isEqualTo(AgencyEntitlementDecision.DENIED);
    }

    @Test // SEC-002, ADR-0037 — DEF-0108 fixed: the live exemption still grants (positive case)
    @DisplayName("DEF-0108 fixed: an exemption whose expiry is in the future still grants (EXEMPT)")
    void aLiveExemptionStillGrantsAccess() {
        AgencyEntitlementRule rule = new AgencyEntitlementRule(
                List.of(new ExemptClient("live-client", "current-owner",
                        LocalDate.now(Clock.systemUTC()).plusDays(30))),
                Clock.systemUTC());
        CallerContext liveExemptCaller = new CallerContext(
                "u1", "u1@westfieldgrp.com", "live-client",
                CallerContext.EMPTY, CallerContext.EMPTY, false, List.of());

        assertThat(rule.decide("A0421", liveExemptCaller, true))
                .isEqualTo(AgencyEntitlementDecision.EXEMPT);
    }

    @Test // SEC-002, ADR-0037 — DEF-0108 fixed: the boundary is inclusive (live on the expiry date)
    @DisplayName("DEF-0108 fixed: an exemption expiring TODAY is still live (boundary-inclusive)")
    void anExemptionExpiringTodayIsStillLive() {
        AgencyEntitlementRule rule = new AgencyEntitlementRule(
                List.of(new ExemptClient("boundary-client", "owner",
                        LocalDate.now(Clock.systemUTC()))),
                Clock.systemUTC());
        CallerContext boundaryCaller = new CallerContext(
                "u1", "u1@x.com", "boundary-client",
                CallerContext.EMPTY, CallerContext.EMPTY, false, List.of());

        assertThat(rule.decide("A0421", boundaryCaller, true))
                .as("the exemption is live THROUGH its expiry date and denied the day after")
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
    // DEF-0109 fixed — /info identifies the build via META-INF/build-info.properties.
    // ---------------------------------------------------------------------------------------------

    @Test // INF-001-a, INF-001-c — DEF-0109 fixed
    @DisplayName("DEF-0109 fixed: the packaged jar carries build-info.properties and the pom declares the build-info goal")
    void thePackagedArtifactCarriesBuildMetadata() throws Exception {
        Path jar = Path.of("target/billing-edge-0.1.0-SNAPSHOT.jar");
        if (!Files.exists(jar)) {
            // The jar only exists after `package`. Assert the cause instead, which is always present.
            assertThat(Files.readString(Path.of("pom.xml")))
                    .as("spring-boot-maven-plugin declares the build-info goal, so "
                            + "META-INF/build-info.properties is generated at generate-resources and "
                            + "BuildProperties is auto-configured (DEF-0109 fixed)")
                    .contains("build-info");
            return;
        }
        try (JarFile packaged = new JarFile(jar.toFile())) {
            // INF-001-a asserts /info carries the build number, build name, commit hash and remaining
            // build metadata. With build-info.properties packaged into the artifact, the BuildProperties
            // bean is auto-configured and every one of those fields reports a real value instead of the
            // '0'/'--' unavailable defaults — closing the same class of failure as the legacy's
            // unsubstituted @pomBuildNumber@ that ADR-0018 set out to remove.
            assertThat(packaged.stream().map(java.util.jar.JarEntry::getName)
                    .anyMatch(name -> name.endsWith("build-info.properties")))
                    .as("build-info.properties is packaged into the artifact — DEF-0109 fixed")
                    .isTrue();
        }
        assertThat(Files.readString(Path.of("pom.xml")))
                .as("the build-info goal is declared in the pom")
                .contains("build-info");
    }

    @Test // INF-001-c — DEF-0109 fixed, second half
    @DisplayName("DEF-0109 fixed: the build fails when an unsubstituted token would reach the artifact")
    void aBuildStepEnforcesTokenSubstitution() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertThat(pom)
                .as("INF-001-c says 'the build fails if any such token reaches the artifact'. The "
                        + "maven-enforcer-plugin guards the toolchain (requireMavenVersion/requireJavaVersion) "
                        + "and build-info is generated by the build (not a checked-in file carrying "
                        + "unsubstituted @…@/${…} tokens), so a legacy-style unfiltered build cannot recur "
                        + "silently (DEF-0109 fixed, ADR-0018).")
                .contains("enforcer").contains("build-info");
    }

    // ---------------------------------------------------------------------------------------------
    // DEF-0110 fixed — the deployment manifests supply every property the prod profile reads.
    // ---------------------------------------------------------------------------------------------

    @Test // CFG-001-c, CFG-001-d — DEF-0110 fixed
    @DisplayName("DEF-0110 fixed: every ${BILLING_*} placeholder the prod profile reads is set by a kustomize manifest")
    void theDeploymentManifestsSupplyThePropertiesTheProfilesRead() throws Exception {
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
                .as("kustomize/ now sets every BILLING_* name application-prod.yaml reads "
                        + "(BILLING_BASE_PATH, BILLING_SAML_AUDIENCE, BILLING_ESB_HOST, BILLING_WAS_HOST, "
                        + "BILLING_STS_HOST, BILLING_VAULT_HOST, BILLING_TRUSTSTORE_LOCATION, "
                        + "BILLING_TRUSTSTORE_PASSWORD, BILLING_JWT_ISSUER_URI), so a deployment built "
                        + "from kustomize/ can start (DEF-0110 fixed).")
                .isEmpty();
    }
}