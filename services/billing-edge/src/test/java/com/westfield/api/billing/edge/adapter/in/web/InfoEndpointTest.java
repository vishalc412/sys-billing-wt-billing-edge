package com.westfield.api.billing.edge.adapter.in.web;

import com.westfield.api.billing.edge.adapter.out.build.BuildMetadataAdapter;
import com.westfield.api.billing.edge.domain.api.ApiResource;
import com.westfield.api.billing.edge.domain.api.ApiResourceTable;
import com.westfield.api.billing.edge.domain.api.ContractViolation;
import com.westfield.api.billing.edge.domain.api.InboundAdmissionRule;
import com.westfield.api.billing.edge.domain.info.BuildInformation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * INF-001 — the build provenance endpoint (N-0011, N-0043, N-0044).
 *
 * <p>The only endpoint that calls no back end and obtains no security assertion, and the only one
 * whose whole purpose is to say which artifact is running. Two legacy properties are preserved and
 * two legacy traps are designed out:
 * <ul>
 *   <li><b>preserved</b> — it always answers, with {@code "0"} for the build number and {@code "--"}
 *       for text when a value is unavailable, so monitoring never handles a missing key; and it
 *       requires a bearer token, so it cannot serve as an unauthenticated health probe
 *       (N-0043 ec 1);</li>
 *   <li><b>corrected</b> — an unsubstituted Maven token becomes a build failure rather than a value
 *       served to a caller (N-0011 ec, ADR-0018); and the timestamp carries an explicit offset
 *       instead of inheriting the container's implicit timezone (N-0044 ec 2).</li>
 * </ul>
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("INF-001 build provenance endpoint")
class InfoEndpointTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-08-17T09:30:00Z"), ZoneId.of("UTC"));

    private static BuildMetadataAdapter adapterWith(Map<String, String> buildValues, String gitCommit) {
        Properties properties = new Properties();
        buildValues.forEach(properties::setProperty);
        BuildProperties buildProperties = new BuildProperties(properties);
        ObjectProvider<BuildProperties> provider = new ObjectProvider<>() {
            @Override
            public BuildProperties getObject() {
                return buildProperties;
            }

            @Override
            public BuildProperties getObject(Object... args) {
                return buildProperties;
            }

            @Override
            public BuildProperties getIfAvailable() {
                return buildValues.isEmpty() ? null : buildProperties;
            }

            @Override
            public BuildProperties getIfUnique() {
                return getIfAvailable();
            }
        };
        return new BuildMetadataAdapter(provider, FIXED, gitCommit);
    }

    @Test // INF-001-a
    @DisplayName("a: a successful call returns the build number, name, commit, remaining metadata and the server time, at 200")
    void returnsTheFullBuildProvenanceAt200() {
        BuildMetadataAdapter adapter = adapterWith(
                Map.of("number", "4711", "name", "billing-edge", "version", "0.1.0-SNAPSHOT",
                        "group", "com.westfield.api", "artifact", "billing-edge"),
                "9f3c1ab");

        ResponseEntity<Map<String, Object>> response = new InfoController(adapter).buildInformation();

        assertThat(response.getStatusCode().value())
                .as("the legacy sets no status here and takes the listener default of 200 (N-0043 ec 2)")
                .isEqualTo(200);
        assertThat(response.getBody()).containsEntry("buildNumber", "4711");
        assertThat(response.getBody()).containsEntry("buildName", "billing-edge");
        assertThat(response.getBody()).containsEntry("gitCommit", "9f3c1ab");
        assertThat(response.getBody()).containsEntry("otherBuildInfo", "0.1.0-SNAPSHOT");
        assertThat(response.getBody()).containsKey("timestamp");
    }

    @Test // INF-001-b
    @DisplayName("b: unavailable metadata still answers 200 using 0 and the two-character placeholder")
    void unavailableMetadataStillAnswers() {
        // N-0044: '0' for the number and '--' for text. The point of the defaults is that no key is
        // ever missing, so a monitoring query never has to distinguish "absent" from "unknown".
        BuildInformation information = BuildInformation.of(null, null, "  ", null, OffsetDateTime.now(FIXED));

        assertThat(information.buildNumber()).isEqualTo(BuildInformation.BUILD_NUMBER_DEFAULT).isEqualTo("0");
        assertThat(information.buildName()).isEqualTo(BuildInformation.TEXT_DEFAULT).isEqualTo("--");
        assertThat(information.gitCommit()).isEqualTo("--");
        assertThat(information.otherBuildInfo()).isEqualTo("--");

        Map<String, Object> body = information.asMap();
        assertThat(body).containsOnlyKeys("buildNumber", "buildName", "gitCommit", "otherBuildInfo", "timestamp");

        // And the endpoint still answers 200 with the full key set when nothing is available at all.
        ResponseEntity<Map<String, Object>> response =
                new InfoController(adapterWith(Map.of(), "")).buildInformation();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody())
                .containsEntry("buildNumber", "0")
                .containsEntry("buildName", "--")
                .containsEntry("gitCommit", "--");
    }

    @Test // INF-001-c
    @DisplayName("c: an unsubstituted build-tool token fails the boot instead of being served")
    void unsubstitutedTokensFailTheBuild() {
        // The legacy trap, exactly (N-0011 ec): buildInfo.properties is checked in carrying Maven
        // filter tokens. An unfiltered build leaves them in place, the property then resolves
        // SUCCESSFULLY to its own token text, and so the '0'/'--' defaults never fire — /info happily
        // reports "@pomBuildNumber@" as a build number. The defaults cannot catch it because nothing
        // failed. ADR-0018 asks for this criterion by name and turns it into a loud failure.
        assertThat(BuildInformation.isUnsubstitutedToken("@pomBuildNumber@")).isTrue();
        assertThat(BuildInformation.isUnsubstitutedToken("${project.version}")).isTrue();
        assertThat(BuildInformation.isUnsubstitutedToken("4711")).isFalse();
        assertThat(BuildInformation.isUnsubstitutedToken("--")).isFalse();
        assertThat(BuildInformation.isUnsubstitutedToken(null)).isFalse();

        assertThatThrownBy(() -> adapterWith(
                Map.of("number", "@pomBuildNumber@", "name", "billing-edge", "version", "1"), "abc")
                .validateNoUnsubstitutedTokens())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@pomBuildNumber@")
                .hasMessageContaining("must never serve it");

        // The packaged artifact this build produces must pass the same check.
        assertThatCode(() -> adapterWith(
                Map.of("number", "4711", "name", "billing-edge", "version", "0.1.0-SNAPSHOT"), "9f3c1ab")
                .validateNoUnsubstitutedTokens())
                .doesNotThrowAnyException();
    }

    @Test // INF-001-d
    @DisplayName("d: /info without a bearer token is rejected — it is not an unauthenticated health probe")
    void infoRequiresABearerToken() {
        // N-0043 ec 1: the legacy covers /info with the 'secured' trait like every other resource.
        // Relaxing it would publish build provenance — including the source commit — to anyone who
        // can reach the port, and the listener binds 0.0.0.0 (N-0013 ec 2). Kubernetes-style probes
        // use the actuator endpoints instead.
        InboundAdmissionRule rule = new InboundAdmissionRule(new ApiResourceTable());

        Optional<ContractViolation> violation =
                rule.admit("GET", "/info", Map.of(), "application/json", null, null);

        assertThat(violation)
                .as("an /info request with no Authorization header never reaches the implementation")
                .contains(ContractViolation.BAD_REQUEST);

        assertThat(rule.admit("GET", "/info", Map.of(), "application/json", null, "Bearer token"))
                .isEmpty();
    }

    @Test // INF-001-e
    @DisplayName("e: the timestamp carries an explicit offset rather than the container's implicit timezone")
    void timestampCarriesAnExplicitOffset() {
        // N-0044 ec 2: the legacy calls now() and inherits whatever timezone the container happens
        // to have, which is pinned nowhere. A timestamp whose meaning depends on a container setting
        // is not evidence of anything — two pods can disagree about when the same build answered.
        BuildInformation information = adapterWith(
                Map.of("number", "4711", "name", "billing-edge", "version", "1"), "abc").current();

        assertThat(information.timestamp().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(String.valueOf(information.asMap().get("timestamp")))
                .as("the serialised value must state its offset")
                .isEqualTo("2026-08-17T09:30Z");
    }

    @Test // INF-001-g
    @DisplayName("g: /info makes no backend call and obtains no security assertion")
    void infoTouchesNoBackendAndMintsNoAssertion() {
        // Six of the eight operations begin with a WS-Trust exchange (N-0071). /info is one of the
        // two that does not, and keeping it that way is what lets it stay cheap enough to poll.
        // Asserted structurally: the controller's only collaborator is the build metadata reader.
        assertThat(InfoController.class.getDeclaredConstructors()).hasSize(1);
        assertThat(InfoController.class.getDeclaredConstructors()[0].getParameterTypes())
                .as("/info depends on the build metadata and on nothing else — no assertion "
                        + "provider, no backend client, no credential provider")
                .containsExactly(BuildMetadataAdapter.class);

        assertThat(BuildMetadataAdapter.class.getDeclaredConstructors()[0].getParameterTypes())
                .doesNotContain(com.westfield.api.billing.platform.spi.BackendAssertionProvider.class);

        // And the resource table agrees: /info carries no environment trait and needs no backend.
        ApiResource info = new ApiResourceTable().find("GET", "/info").orElseThrow();
        assertThat(info.requiresEnvironmentParam()).isFalse();
        assertThat(info.implemented()).isTrue();
    }
}
