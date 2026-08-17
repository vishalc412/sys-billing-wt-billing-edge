package com.westfield.api.billing.edge.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CFG-001 and CFG-002 against the migrated configuration itself (ADR-0018, ADR-0039).
 *
 * <p>These read the profile files directly rather than booting a context per environment. That is
 * deliberate: five of the six profiles resolve their back ends from deployment-supplied environment
 * variables with no committed default, so booting them would either fail for the wrong reason or
 * require inventing values — and the invented values, not the migrated ones, would then be what the
 * test asserted. Reading the files asserts what actually ships.
 *
 * <p>The legacy shape being replaced is six {@code <env>.properties} files selected by
 * {@code mule.env}, with three recorded oddities carried forward on purpose and one removed:
 * {@code pasmIdBilling} is referenced by nothing and is kept (R-022); stage points at the TEST ESB
 * and is kept (R-023); the production file names a TEST PingFederate host, wired to nothing, and is
 * kept as a recorded note; and the single truststore ciphertext shared byte-identically across all
 * six environments is NOT kept, in any form (ADR-0039).
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("CFG-001 / CFG-002 migrated environment configuration")
class EnvironmentProfileTest {

    /** The six environments the legacy defines a property file for. There is no seventh. */
    private static final List<String> ENVIRONMENTS = List.of("local", "dev", "test", "stage", "perf", "prod");

    private static final Map<String, PropertySource<?>> PROFILES = loadProfiles();

    @Test // CFG-001-c
    @DisplayName("c: every environment resolves its own identity and no value leaks in from another")
    void eachEnvironmentResolvesItsOwnValues() {
        // The API Manager instance id is the value that must never be shared: it is what binds this
        // deployment to its own policy set and its own analytics (N-0004 ec 1). Six environments,
        // six distinct ids.
        Map<String, String> apiIds = new LinkedHashMap<>();
        for (String environment : ENVIRONMENTS) {
            String id = string(environment, "billing.api.id");
            assertThat(id).as("%s must declare an API instance id", environment).isNotBlank();
            apiIds.put(environment, id);
        }
        assertThat(apiIds.values())
                .as("no environment may share another's API Manager instance id")
                .doesNotHaveDuplicates();

        // Recorded verbatim from the legacy files (N-0004 ec 1).
        assertThat(apiIds.get("dev")).isEqualTo("16888213");
        assertThat(apiIds.get("test")).isEqualTo("15653875");
        assertThat(apiIds.get("stage")).isEqualTo("15668558");
        assertThat(apiIds.get("perf")).isEqualTo("17349181");
        assertThat(apiIds.get("prod")).isEqualTo("15710690");

        // Each environment supplies its own audience. That, and only that, is what stops a
        // non-production assertion being accepted by the production billing service (N-0019).
        for (String environment : ENVIRONMENTS) {
            assertThat(string(environment, "billing.saml.audience"))
                    .as("%s must scope its assertion audience", environment)
                    .isNotBlank();
        }

        // Every environment resolves all four back ends; none inherits another's host.
        for (String environment : ENVIRONMENTS) {
            for (String backend : List.of("esb", "was", "sts", "vault")) {
                assertThat(string(environment, "billing.backend." + backend + ".host"))
                        .as("%s must resolve its own %s host", environment, backend)
                        .isNotBlank();
            }
        }
    }

    @Test // CFG-001-e
    @DisplayName("e: all eight endpoints can start locally, unlike the legacy where three could not")
    void localResolvesEveryBackendUnlikeTheLegacy() {
        // A DELIBERATE DIVERGENCE (ADR-0018). The legacy local.properties omits primaryAccount.host
        // and primaryAccount.path entirely, so the three /primaryAccount endpoints cannot start under
        // mule.env=local at all (N-0003 ec 2, N-0101 ec 1). Nothing about that is observable to a
        // consumer, and the alternative is a local environment in which three of eight endpoints
        // cannot be worked on — which is how a gap like this survives for years.
        for (String backend : List.of("esb", "was", "sts", "vault")) {
            String host = string("local", "billing.backend." + backend + ".host");
            assertThat(host)
                    .as("local must resolve the %s host without a deployment-supplied value", backend)
                    .isNotBlank()
                    .as("local must carry a usable default so a workstation needs no secrets")
                    .contains(":");
            assertThat(hasDefault(host))
                    .as("local's %s host must have an inline default (%s)", backend, host)
                    .isTrue();
        }
        assertThat(string("local", "billing.api.base-path"))
                .as("the legacy local listener path, verbatim")
                .isEqualTo("/sapi-billing/v1");
    }

    @Test // CFG-001-f
    @DisplayName("f: production keeps its own response timeout and every lower environment keeps the shared one")
    void productionResponseTimeoutDiffersFromEveryLowerEnvironment() {
        // N-0003 ec 3: response.timeout is 20000 in production and 10000 everywhere else. Migrated
        // verbatim rather than harmonised — harmonising would change production's tolerance for a
        // slow back end, which is a live-traffic decision nobody has taken.
        assertThat(string("prod", "billing.backend.was.response-timeout")).isEqualTo("20s");
        for (String environment : ENVIRONMENTS) {
            if (environment.equals("prod")) {
                continue;
            }
            assertThat(string(environment, "billing.backend.was.response-timeout"))
                    .as("%s keeps the non-production timeout", environment)
                    .isEqualTo("10s");
        }

        // The consequence, recorded rather than assumed away: a latency measurement taken anywhere
        // but production is measured against a different bound and is therefore not evidence about
        // production. Any NFR run outside prod must be labelled unrepresentative.
        assertThat(string("prod", "billing.backend.was.response-timeout"))
                .isNotEqualTo(string("perf", "billing.backend.was.response-timeout"));
    }

    @Test // CFG-001-g
    @DisplayName("g: stage still points at the TEST ESB, verbatim, and the file says why")
    void stageStillPointsAtTheTestEsb() {
        // N-0101 ec 2. Carried forward EXACTLY, not corrected: nobody knows whether a stage ESB
        // exists (R-023), so "fixing" the hostname could break stage outright. The startup WARN
        // asserted in StartupConfigurationValidatorTest is what makes it impossible to inherit
        // unknowingly, and the comment in the file is what stops the next reader "tidying" it.
        assertThat(string("stage", "billing.backend.esb.host"))
                .as("the legacy stage.properties value, unchanged")
                .contains("tst2esb");

        assertThat(rawText("application-stage.yaml"))
                .as("the oddity must be explained where a reader will find it")
                .contains("tst2esb")
                .contains("R-023");
    }

    @Test // CFG-001-h
    @DisplayName("h: every defined-but-unreferenced legacy property is carried forward with a justification")
    void unreferencedLegacyPropertiesAreCarriedForwardWithAReason() {
        // R-022 kept visible instead of resolved by deletion. Two properties in the legacy are
        // defined and read by nothing:
        //
        //  1. pasmIdBilling — in all six files. Kept: a property defined six times and used nowhere
        //     is the shape of something an external process greps for, it costs nothing to keep, and
        //     dropping it wrongly is irreversible.
        for (String environment : ENVIRONMENTS) {
            assertThat(string(environment, "billing.pasm-id-billing"))
                    .as("%s must still carry pasmIdBilling", environment)
                    .isNotBlank();
        }
        assertThat(string("prod", "billing.pasm-id-billing"))
                .as("1991 in production, 1988 everywhere else — verbatim")
                .isEqualTo("1991");
        assertThat(string("test", "billing.pasm-id-billing")).isEqualTo("1988");

        //  2. ping.host in prod.properties — a TEST PingFederate hostname sitting in the PRODUCTION
        //     file, wired to nothing (N-0101 ec 3). Recorded rather than dropped, and deliberately
        //     NOT connected to billing.backend.sts.host, which is what the STS call actually reads.
        assertThat(string("prod", "legacy-unreferenced.ping-host"))
                .isEqualTo("idpa1-test.westfieldgrp.com");
        assertThat(string("prod", "billing.backend.sts.host"))
                .as("the recorded oddity must not be wired to the property that is actually read")
                .isNotEqualTo("idpa1-test.westfieldgrp.com");
        assertThat(rawText("application-prod.yaml"))
                .as("a carried-forward oddity without a recorded reason is just clutter")
                .contains("N-0101 ec 3");
    }

    @Test // CFG-002-a
    @DisplayName("a: no deployed environment commits a secret value; each resolves it at deployment time")
    void noDeployedEnvironmentCommitsASecret() {
        for (String environment : ENVIRONMENTS) {
            String password = string(environment, "billing.truststore.password");
            assertThat(password).as("%s must declare a truststore password", environment).isNotBlank();

            if (environment.equals("local")) {
                // The one exception, and it is not a secret: a workstation default so a developer
                // needs no vault access. It protects a throwaway local truststore and nothing else.
                assertThat(password).startsWith("${").contains("local-development-only");
                continue;
            }
            assertThat(password)
                    .as("%s must resolve its truststore password from the deployment, with no "
                            + "committed fallback", environment)
                    .isEqualTo("${BILLING_TRUSTSTORE_PASSWORD}");
            assertThat(hasDefault(password))
                    .as("%s must not carry an inline default for a secret", environment)
                    .isFalse();
        }
    }

    @Test // CFG-002-c
    @DisplayName("c: the one ciphertext shared by all six environments is not carried forward in any form")
    void theSharedLegacyCiphertextIsNotCarriedForward() {
        // N-0102 and the N-0007 defect: the legacy ships ONE byte-identical encrypted truststore
        // password across all six environments including production, and the key that decrypts it is
        // in no repository at all. ADR-0039 generates a new password per environment and forbids the
        // old material being carried forward in any form.
        //
        // Because each environment now resolves its own value from its own deployment, no two
        // environments can share a secret by construction — which is the property the criterion asks
        // for, and it is stronger than comparing two committed strings.
        for (String environment : ENVIRONMENTS) {
            String text = rawText("application-" + environment + ".yaml");
            assertThat(text)
                    .as("%s must contain no Mule secure-property ciphertext", environment)
                    .doesNotContain("![");
            assertThat(text)
                    .as("%s must not reference the legacy decryption key", environment)
                    .doesNotContain("secure.key")
                    .doesNotContain("secure::");
        }
        assertThat(rawText("application-prod.yaml"))
                .as("production must say why its password is generated rather than inherited")
                .contains("ADR-0039");
    }

    @Test // CFG-002-e
    @DisplayName("e: no code path installs a permissive trust manager, so certificate validation cannot be off")
    void certificateValidationIsNeverDisabled() {
        // The runtime behaviour — an untrusted certificate being refused — is the JDK default. What
        // can silently remove it is a developer disabling validation to get past a local certificate
        // problem, which is exactly the kind of change that ships. This scans the service's own
        // sources for every idiom that does it. A source scan is a weak test of TLS and a strong
        // test of the thing that actually goes wrong.
        List<String> offenders = new ArrayList<>();
        for (Path source : javaSources()) {
            String text = read(source);
            for (String idiom : List.of("TrustAllCerts", "X509TrustManager", "setDefaultHostnameVerifier",
                    "NoopHostnameVerifier", "ALLOW_ALL_HOSTNAME_VERIFIER", "trustAllCertificates",
                    "InsecureTrustManagerFactory")) {
                if (text.contains(idiom)) {
                    offenders.add(source.getFileName() + " uses " + idiom);
                }
            }
        }
        assertThat(offenders)
                .as("certificate validation must never be bypassed in service code")
                .isEmpty();

        // And the trust material every environment validates against is required configuration, so
        // there is no "no truststore configured" state to fall back from.
        for (String environment : ENVIRONMENTS) {
            assertThat(string(environment, "billing.truststore.location"))
                    .as("%s must configure trust material", environment)
                    .isNotBlank();
        }
    }

    @Test // CFG-002-f
    @DisplayName("f: one trust configuration serves both back ends, as in the legacy")
    void bothBackendsAreTrustedByTheSameConfiguration() {
        // N-0014 ec 1: the legacy declares a single TLS context and reuses it for the ESB and the
        // WAS back end. Preserved. Splitting it would be tidier and would double the number of
        // rotations operations has to perform, for no observable benefit.
        for (String environment : ENVIRONMENTS) {
            String text = rawText("application-" + environment + ".yaml");
            assertThat(countOccurrences(text, "truststore:"))
                    .as("%s must declare exactly one trust context for all outbound calls", environment)
                    .isEqualTo(1);
            assertThat(string(environment, "billing.truststore.location")).isNotBlank();
        }
    }

    @Test // CFG-002-g
    @DisplayName("g: trust material lives outside the artifact, so a rotation needs no rebuild")
    void trustMaterialCanRotateWithoutRebuildingTheArtifact() {
        // A truststore packaged inside the jar makes every certificate rotation a build, a release
        // and a deployment. Every deployed environment resolves both the location and the password
        // from the deployment, so supplying new material is a restart at worst (R-030).
        for (String environment : ENVIRONMENTS) {
            if (environment.equals("local")) {
                continue;
            }
            String location = string(environment, "billing.truststore.location");
            assertThat(location)
                    .as("%s must take its trust material from the deployment", environment)
                    .isEqualTo("${BILLING_TRUSTSTORE_LOCATION}");
            assertThat(location)
                    .as("%s must not bake trust material into the artifact", environment)
                    .doesNotContain("classpath:");
        }
    }

    // ---------------------------------------------------------------------------------------------

    private static Map<String, PropertySource<?>> loadProfiles() {
        Map<String, PropertySource<?>> loaded = new LinkedHashMap<>();
        YamlPropertySourceLoader yaml = new YamlPropertySourceLoader();
        for (String environment : ENVIRONMENTS) {
            String name = "application-" + environment + ".yaml";
            Resource resource = new ClassPathResource(name);
            if (!resource.exists()) {
                throw new IllegalStateException(
                        "Missing profile " + name + ". Every environment the legacy defines a "
                                + "property file for must have one here (CFG-001-c).");
            }
            try {
                List<PropertySource<?>> sources = yaml.load(environment, resource);
                loaded.put(environment, sources.get(0));
            } catch (IOException unreadable) {
                throw new UncheckedIOException(unreadable);
            }
        }
        return loaded;
    }

    private static String string(String environment, String key) {
        Object value = PROFILES.get(environment).getProperty(key);
        return value == null ? null : value.toString();
    }

    /** True when a {@code ${VAR:default}} reference carries an inline fallback. */
    private static boolean hasDefault(String placeholder) {
        return placeholder.startsWith("${") && placeholder.contains(":");
    }

    private static String rawText(String fileName) {
        try {
            return new String(new ClassPathResource(fileName).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static List<Path> javaSources() {
        Path root = Path.of("src", "main", "java");
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
