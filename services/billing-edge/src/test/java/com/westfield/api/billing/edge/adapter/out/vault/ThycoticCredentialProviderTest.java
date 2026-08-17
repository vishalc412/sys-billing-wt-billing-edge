package com.westfield.api.billing.edge.adapter.out.vault;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.westfield.api.billing.edge.application.port.CredentialProvider;
import com.westfield.api.billing.edge.application.port.ServiceAccountCredentials;
import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.edge.testsupport.Fixtures;
import com.westfield.api.billing.edge.testsupport.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TOK-001 — service-account credentials from Thycotic Secret Server (N-0008, N-0020, N-0072,
 * ADR-0008).
 *
 * <p>The secret store is stubbed with WireMock rather than waited on, per the packet's dependency
 * contract. That is not only a scheduling convenience: the legacy's fetch, cache and outage behaviour
 * is internal to an Exchange module that is not in the source tree (R-007), so there is no real
 * behaviour to integrate against. The stub is built to the one thing that IS observable — two secret
 * names in, two values out — and the target states its own caching and failure behaviour explicitly
 * rather than inheriting an unknown.
 *
 * <p>Two criteria here are deliberate improvements over the legacy with no consumer-visible effect
 * (f and g). The legacy places the credentials in ordinary flow variables that persist for the rest
 * of the request and cross the flow-ref boundary back into the calling flow, so any later processor
 * that dumped all variables would print them (N-0071 ec 3, N-0072 ec 4).
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("TOK-001 service-account credential retrieval")
class ThycoticCredentialProviderTest {

    private static final String USERNAME_SECRET = "billing.username";
    private static final String PASSWORD_SECRET = "billing.password";

    private WireMockServer vault;
    private BillingEdgeProperties properties;
    private MutableClock clock;
    private ObjectMapper objectMapper;

    @BeforeEach
    void startVault() {
        vault = new WireMockServer(options().dynamicPort().dynamicHttpsPort());
        vault.start();
        properties = Fixtures.validProperties();
        properties.getBackend().getVault().setHost("http://localhost:" + vault.port());
        properties.getCredentials().setCacheTtlSeconds(60);
        clock = new MutableClock(Instant.parse("2026-08-17T09:00:00Z"));
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void stopVault() {
        vault.stop();
    }

    private ThycoticCredentialProvider provider() {
        return new ThycoticCredentialProvider(properties, objectMapper, clock);
    }

    private void stubSecret(String name, String value) {
        vault.stubFor(get(urlPathEqualTo("/secrets/" + name))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"value\":\"" + value + "\"}")));
    }

    @Test // TOK-001-a
    @DisplayName("a: the credentials come from the secret store and appear nowhere in the artifact")
    void credentialsComeFromTheStoreAndNotFromTheArtifact() {
        stubSecret(USERNAME_SECRET, "svc_billing");
        stubSecret(PASSWORD_SECRET, "s3cr3t-from-the-vault");

        try (ServiceAccountCredentials credentials = provider().serviceAccount()) {
            assertThat(credentials.username()).isEqualTo("svc_billing");
            assertThat(new String(credentials.password())).isEqualTo("s3cr3t-from-the-vault");
        }
        vault.verify(getRequestedFor(urlPathEqualTo("/secrets/" + USERNAME_SECRET)));
        vault.verify(getRequestedFor(urlPathEqualTo("/secrets/" + PASSWORD_SECRET)));

        // "Not present in the deployment artifact in any form" is the half of the criterion that a
        // stub cannot prove, so it is asserted against the shipped configuration directly: what the
        // artifact carries is the NAME of each secret, never a value.
        for (Path resource : resources()) {
            String text = read(resource);
            assertThat(text)
                    .as("%s must not carry a service-account credential", resource.getFileName())
                    .doesNotContain("svc_billing")
                    .doesNotContain("password: \"")
                    .doesNotContain("![");
        }
        assertThat(properties.getCredentials().getUsernameSecretName()).isEqualTo(USERNAME_SECRET);
    }

    @Test // TOK-001-b
    @DisplayName("b: a rotated credential takes effect without the service being redeployed")
    void rotationTakesEffectWithoutRedeploy() {
        // This is the property that makes the 60s TTL a bound rather than a cache-forever. Whatever
        // the legacy module does internally (R-007), the target's own behaviour is stated: at most
        // one TTL of staleness after an operator rotates a secret, and no restart.
        stubSecret(USERNAME_SECRET, "svc_billing");
        stubSecret(PASSWORD_SECRET, "old-password");

        ThycoticCredentialProvider provider = provider();
        try (ServiceAccountCredentials before = provider.serviceAccount()) {
            assertThat(new String(before.password())).isEqualTo("old-password");
        }

        stubSecret(PASSWORD_SECRET, "rotated-password");

        // Still cached: the rotation is not visible instantly, and that is the stated trade.
        try (ServiceAccountCredentials during = provider.serviceAccount()) {
            assertThat(new String(during.password())).isEqualTo("old-password");
        }

        clock.advance(Duration.ofSeconds(61));

        try (ServiceAccountCredentials after = provider.serviceAccount()) {
            assertThat(new String(after.password()))
                    .as("the rotated value takes effect on TTL expiry, with no redeploy")
                    .isEqualTo("rotated-password");
        }
    }

    @Test // TOK-001-c
    @DisplayName("c: exactly two secret names are consulted, and they are the legacy's two")
    void exactlyTwoSecretNamesAreConsulted() {
        // N-0008 ec 3. The legacy hardcodes these two names, so renaming a secret in the vault breaks
        // every backend call (N-0072 ec 1). Here they are configuration — which changes nothing
        // observable and makes the coupling visible — but the DEFAULTS must remain the legacy names,
        // or a deployment that does not override them silently looks for secrets that do not exist.
        stubSecret(USERNAME_SECRET, "svc_billing");
        stubSecret(PASSWORD_SECRET, "s3cr3t");

        try (ServiceAccountCredentials ignored = provider().serviceAccount()) {
            // consume
        }

        assertThat(vault.getAllServeEvents())
                .as("two secrets, two requests — nothing else is fetched from the store")
                .hasSize(2);
        assertThat(vault.getAllServeEvents())
                .extracting(event -> event.getRequest().getUrl())
                .containsExactlyInAnyOrder("/secrets/" + PASSWORD_SECRET, "/secrets/" + USERNAME_SECRET);

        BillingEdgeProperties defaults = new BillingEdgeProperties();
        assertThat(defaults.getCredentials().getUsernameSecretName()).isEqualTo("billing.username");
        assertThat(defaults.getCredentials().getPasswordSecretName()).isEqualTo("billing.password");
    }

    @Test // TOK-001-d
    @DisplayName("d: an unresolvable secret name fails the request and is attributable to credential resolution")
    void unresolvableSecretIsAttributableToCredentialResolution() {
        // The failure must not look like a backend fault. In the legacy the p() call raises and the
        // flow drops into its on-error-continue, so a vault problem reaches the caller as a generic
        // billing error and the on-call engineer starts by paging the billing team (N-0072 ec 2).
        stubSecret(USERNAME_SECRET, "svc_billing");
        vault.stubFor(get(urlPathEqualTo("/secrets/" + PASSWORD_SECRET))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> provider().serviceAccount())
                .isInstanceOf(CredentialProvider.CredentialResolutionException.class)
                .hasMessageContaining(PASSWORD_SECRET)
                .hasMessageContaining("could not be resolved");

        // A secret that resolves to no value at all is the same class of failure, not a blank
        // credential handed onward to the STS.
        vault.stubFor(get(urlPathEqualTo("/secrets/" + PASSWORD_SECRET))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"value\":null}")));

        assertThatThrownBy(() -> provider().serviceAccount())
                .isInstanceOf(CredentialProvider.CredentialResolutionException.class)
                .hasMessageContaining("resolved to no value");
    }

    @Test // TOK-001-e
    @DisplayName("e: an unreachable store fails the request the same way for every backend-calling endpoint")
    void anUnreachableStoreFailsUniformly() {
        // R-007 is open: nobody knows whether a vault outage in the legacy is a startup failure or a
        // per-request failure. ADR-0008 fetches per request, so the target's answer is per-request
        // and — the part that matters — IDENTICAL on every endpoint, because there is one provider
        // and one failure type rather than six copies that could drift.
        vault.stop();

        CredentialProvider provider = provider();
        for (int endpoint = 0; endpoint < 3; endpoint++) {
            assertThatThrownBy(provider::serviceAccount)
                    .as("every backend-calling endpoint sees the same failure")
                    .isInstanceOf(CredentialProvider.CredentialResolutionException.class)
                    .hasMessageContaining("unreachable");
        }
    }

    @Test // TOK-001-f
    @DisplayName("f: once the exchange is done the credentials are no longer held in request-scoped state")
    void credentialsAreClearedAfterTheExchange() {
        // A deliberate improvement with no consumer-visible effect. The password is mutable, is
        // overwritten in place when the exchange finishes, and cannot be serialised or logged by
        // accident. The legacy leaves both values in flow variables for the remainder of the request.
        stubSecret(USERNAME_SECRET, "svc_billing");
        stubSecret(PASSWORD_SECRET, "s3cr3t");

        ServiceAccountCredentials credentials = provider().serviceAccount();
        assertThat(credentials.isCleared()).isFalse();

        credentials.close();

        assertThat(credentials.isCleared())
                .as("the password material is overwritten, not merely dereferenced")
                .isTrue();
        assertThat(new String(credentials.password())).isEqualTo("\0\0\0\0\0\0");
    }

    @Test // TOK-001-g
    @DisplayName("g: no log entry after the exchange contains the service-account username or password")
    void neitherCredentialEverReachesALogEntry() {
        stubSecret(USERNAME_SECRET, "svc_billing");
        stubSecret(PASSWORD_SECRET, "s3cr3t-must-never-be-logged");

        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            try (ServiceAccountCredentials credentials = provider().serviceAccount()) {
                // The object's own toString is the most likely accidental route into a log line, so
                // it is exercised explicitly rather than left to chance.
                org.slf4j.LoggerFactory.getLogger(ThycoticCredentialProviderTest.class)
                        .info("credentials in hand: {}", credentials);
            }

            // And a failure path, which is where secrets usually escape: exception messages.
            vault.stubFor(get(urlPathMatching("/secrets/.*")).willReturn(aResponse().withStatus(500)));
            try {
                provider().serviceAccount();
            } catch (CredentialProvider.CredentialResolutionException expected) {
                org.slf4j.LoggerFactory.getLogger(ThycoticCredentialProviderTest.class)
                        .error("credential resolution failed", expected);
            }

            String everythingLogged = appender.list.stream()
                    .map(event -> event.getFormattedMessage() + " "
                            + (event.getThrowableProxy() == null ? "" : event.getThrowableProxy().getMessage()))
                    .reduce("", (a, b) -> a + "\n" + b);

            assertThat(everythingLogged)
                    .as("the secret NAME may appear in a log line; the VALUE never may")
                    .doesNotContain("s3cr3t-must-never-be-logged")
                    .doesNotContain("svc_billing");
            assertThat(everythingLogged).contains("<redacted>");
        } finally {
            detachAppender(appender);
        }
    }

    @Test // TOK-001-h
    @DisplayName("h: the store's certificate is validated and an untrusted one refuses the connection")
    void theStoreCertificateIsValidated() {
        // WireMock's HTTPS port presents a self-signed certificate that no default trust store
        // contains. The call must therefore FAIL — which is the assertion: nothing in this adapter
        // installs a permissive trust manager, so an untrusted certificate is refused rather than
        // accepted with a warning. A test that passed here would mean validation had been disabled.
        properties.getBackend().getVault().setHost("https://localhost:" + vault.httpsPort());
        stubSecret(USERNAME_SECRET, "svc_billing");
        stubSecret(PASSWORD_SECRET, "s3cr3t");

        Throwable failure = catchThrowable(() -> provider().serviceAccount());

        assertThat(failure)
                .isInstanceOf(CredentialProvider.CredentialResolutionException.class)
                .hasMessageContaining("unreachable");
        assertThat(causeChain(failure))
                .as("the connection must fail on certificate validation, not succeed anyway")
                .anyMatch(cause -> cause instanceof javax.net.ssl.SSLException);
    }

    // ---------------------------------------------------------------------------------------------

    /** Every throwable in the cause chain, so a wrapped TLS failure is still findable. */
    private static List<Throwable> causeChain(Throwable failure) {
        List<Throwable> chain = new java.util.ArrayList<>();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            chain.add(current);
            if (chain.size() > 20) {
                break;
            }
        }
        return chain;
    }

    private static ListAppender<ILoggingEvent> attachAppender() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(context);
        appender.start();
        ch.qos.logback.classic.Logger root = context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.DEBUG);
        root.addAppender(appender);
        return appender;
    }

    private static void detachAppender(ListAppender<ILoggingEvent> appender) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME).detachAppender(appender);
        appender.stop();
    }

    private static List<Path> resources() {
        Path root = Path.of("src", "main", "resources");
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).toList();
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
}
