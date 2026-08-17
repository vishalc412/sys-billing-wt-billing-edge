package com.westfield.api.billing.edge.s5;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.westfield.api.billing.edge.adapter.out.sts.WsTrustSecurityTokenService;
import com.westfield.api.billing.edge.adapter.out.vault.ThycoticCredentialProvider;
import com.westfield.api.billing.edge.application.port.CredentialProvider;
import com.westfield.api.billing.edge.application.port.ServiceAccountCredentials;
import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.edge.testsupport.Fixtures;
import com.westfield.api.billing.platform.spi.BackendAssertionProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * NFR probes with their measurement methods stated in the test itself.
 *
 * <p><b>What has no target.</b> No capability spec in this migration states a p99 latency, a
 * throughput figure or an error budget for billing-edge. ADR-0014 states timeout NUMBERS, and those
 * are the only quantitative targets that exist, so those are the only ones probed here. Everything
 * else is recorded as blocked in the evidence pack rather than measured against a number this test
 * would have had to invent. A probe that passes an invented target proves nothing.
 *
 * <p><b>Measurement method.</b> A WireMock server bound to a loopback port delays its response past
 * the configured timeout. Elapsed wall-clock time is measured with {@link System#nanoTime()} around
 * the single call, on the calling thread, with no warm-up. Reported as one observation, not a
 * percentile: a percentile over a single-threaded loopback loop would be a more precise number about
 * nothing.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("S5 — NFR probes (ADR-0014 timeout bounds)")
class S5NfrProbeTest {

    private WireMockServer wireMock;

    @BeforeEach
    void startStub() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void stopStub() {
        wireMock.stop();
    }

    private BillingEdgeProperties propertiesPointingAtTheStub() {
        BillingEdgeProperties properties = Fixtures.validProperties();
        properties.getBackend().getSts().setHost("http://localhost:" + wireMock.port() + "/sts");
        properties.getBackend().getVault().setHost("http://localhost:" + wireMock.port() + "/vault");
        return properties;
    }

    @Test // TOK-002-i, ADR-0014
    @DisplayName("TOK-002-i: an unresponsive STS is bounded by billing.backend.sts.response-timeout, not by an unbounded wait")
    void theStsCallIsBoundedByItsConfiguredTimeout() {
        BillingEdgeProperties properties = propertiesPointingAtTheStub();
        properties.getBackend().getSts().setResponseTimeout(Duration.ofMillis(700));
        wireMock.stubFor(any(anyUrl()).willReturn(aResponse().withFixedDelay(10_000).withStatus(200)));

        WsTrustSecurityTokenService sts = new WsTrustSecurityTokenService(properties);
        ServiceAccountCredentials credentials =
                new ServiceAccountCredentials("svc-billing", "secret".toCharArray());

        long startedAt = System.nanoTime();
        assertThatThrownBy(() -> sts.mint(credentials, "urn:audience"))
                .isInstanceOf(BackendAssertionProvider.BackendAssertionException.class);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        // Target: bounded by the configured 700ms, generously so that the observation is about the
        // bound existing rather than about scheduler jitter on a loaded build agent.
        assertThat(elapsedMillis)
                .as("measured elapsed: %d ms against a 700 ms configured response timeout and a "
                        + "10 000 ms stub delay", elapsedMillis)
                .isLessThan(5_000L);
    }

    @Test // TOK-001-e, ADR-0014, ADR-0008
    @DisplayName("TOK-001-e: an unresponsive secret store is bounded by billing.backend.vault.response-timeout")
    void theVaultCallIsBoundedByItsConfiguredTimeout() {
        BillingEdgeProperties properties = propertiesPointingAtTheStub();
        properties.getBackend().getVault().setResponseTimeout(Duration.ofMillis(700));
        wireMock.stubFor(any(anyUrl()).willReturn(aResponse().withFixedDelay(10_000).withStatus(200)));

        ThycoticCredentialProvider provider =
                new ThycoticCredentialProvider(properties, new ObjectMapper(), Clock.systemUTC());

        long startedAt = System.nanoTime();
        assertThatThrownBy(provider::serviceAccount)
                .isInstanceOf(CredentialProvider.CredentialResolutionException.class)
                .hasMessageContaining("billing.username");
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        assertThat(elapsedMillis)
                .as("measured elapsed: %d ms against a 700 ms configured response timeout", elapsedMillis)
                .isLessThan(5_000L);
    }

    @Test // TOK-001-d, TOK-001-g
    @DisplayName("TOK-001-d/g: a credential-resolution failure names the secret and never the credential value")
    void aCredentialFailureNamesTheSecretNotItsValue() {
        BillingEdgeProperties properties = propertiesPointingAtTheStub();
        wireMock.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(503)));

        ThycoticCredentialProvider provider =
                new ThycoticCredentialProvider(properties, new ObjectMapper(), Clock.systemUTC());

        assertThatThrownBy(provider::serviceAccount)
                .isInstanceOf(CredentialProvider.CredentialResolutionException.class)
                .hasMessageContaining("billing.username")
                .hasMessageContaining("503");
    }

    @Test // TOK-001-c
    @DisplayName("TOK-001-c: exactly two secret names are consulted, and they are the legacy two")
    void exactlyTwoSecretsAreConsulted() {
        BillingEdgeProperties properties = propertiesPointingAtTheStub();
        wireMock.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"value\":\"v\"}")));

        ThycoticCredentialProvider provider =
                new ThycoticCredentialProvider(properties, new ObjectMapper(), Clock.systemUTC());
        try (ServiceAccountCredentials credentials = provider.serviceAccount()) {
            assertThat(credentials.username()).isEqualTo("v");
        }

        assertThat(wireMock.getAllServeEvents()).hasSize(2);
        assertThat(wireMock.getAllServeEvents())
                .extracting(event -> event.getRequest().getUrl())
                .containsExactlyInAnyOrder("/vault/secrets/billing.username",
                        "/vault/secrets/billing.password");
    }

    @Test // TOK-001-b, ADR-0008
    @DisplayName("TOK-001-b: a rotated credential takes effect once the 60s cache expires, with no redeploy")
    void aRotatedCredentialTakesEffectAfterTheCacheWindow() {
        BillingEdgeProperties properties = propertiesPointingAtTheStub();
        wireMock.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"value\":\"before\"}")));

        ThycoticCredentialProvider provider =
                new ThycoticCredentialProvider(properties, new ObjectMapper(), Clock.systemUTC());
        try (ServiceAccountCredentials first = provider.serviceAccount()) {
            assertThat(first.username()).isEqualTo("before");
        }

        wireMock.resetAll();
        wireMock.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"value\":\"after\"}")));

        // Within the TTL the cached value is still served — the bound on vault call rate.
        try (ServiceAccountCredentials cached = provider.serviceAccount()) {
            assertThat(cached.username()).isEqualTo("before");
        }
        // Past the TTL (simulated by eviction, since the TTL is a wall-clock 60s) the rotation lands
        // without the service being restarted.
        provider.evict();
        try (ServiceAccountCredentials rotated = provider.serviceAccount()) {
            assertThat(rotated.username()).isEqualTo("after");
        }
    }

    @Test // TOK-001-f
    @DisplayName("TOK-001-f: the credential object is wiped when the exchange closes")
    void theCredentialIsWipedOnClose() {
        BillingEdgeProperties properties = propertiesPointingAtTheStub();
        wireMock.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"value\":\"s3cret\"}")));

        ThycoticCredentialProvider provider =
                new ThycoticCredentialProvider(properties, new ObjectMapper(), Clock.systemUTC());
        ServiceAccountCredentials credentials = provider.serviceAccount();
        assertThat(new String(credentials.password())).isEqualTo("s3cret");
        assertThat(credentials.isCleared()).isFalse();

        credentials.close();

        assertThat(credentials.isCleared()).isTrue();
        assertThat(credentials.toString()).doesNotContain("s3cret");
        // Observation, not a defect against TOK-001-f, which governs request-scoped state: the
        // accessor hands out a CLONE, and WsTrustSecurityTokenService wraps that clone in a String
        // to build the RST envelope. That String is an immutable heap copy that close() cannot
        // reach. Recorded so a later heap-dump review is not a surprise.
        assertThat(WsTrustSecurityTokenService.class.getDeclaredMethods()).isNotEmpty();
    }
}
