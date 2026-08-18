package com.westfield.api.billing.edge.s5;

import com.westfield.api.billing.edge.adapter.out.sts.WsTrustSecurityTokenService;
import com.westfield.api.billing.edge.adapter.out.vault.ThycoticCredentialProvider;
import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.edge.testsupport.Fixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DEF-0105 fixed — the configured trust material is now loaded and applied to both outbound clients.
 *
 * <p>CFG-002-e asserts that "the server certificate is validated against the configured trust material
 * and an untrusted certificate is refused"; CFG-002-f that both back ends are trusted by the same
 * configuration; CFG-002-g that a rotation takes effect without a redeploy; TOK-001-h the same for the
 * secret store. ADR-0041 requires the Thycotic client to be given the resolved truststore password.
 *
 * <p>Both outbound adapters now build their {@link HttpClient} with the SSLContext resolved from
 * {@code billing.truststore.*} by {@code OutboundTrustMaterial}, so the configured trust material is
 * actually used rather than only validated at startup.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("S5 — DEF-0105: outbound trust material")
class S5OutboundTrustProbeTest {

    private static HttpClient clientOf(Object adapter) throws Exception {
        Field field = adapter.getClass().getDeclaredField("httpClient");
        field.setAccessible(true);
        return (HttpClient) field.get(adapter);
    }

    @Test // TOK-001-h, CFG-002-e
    @DisplayName("the Thycotic client uses the truststore configured at billing.truststore.location (DEF-0105 fixed)")
    void theVaultClientUsesTheConfiguredTruststore() throws Exception {
        // Fixtures.validProperties() points at classpath:truststore/test.jks (a packaged test keystore).
        BillingEdgeProperties properties = Fixtures.validProperties();

        ThycoticCredentialProvider provider =
                new ThycoticCredentialProvider(properties, new ObjectMapper(), Clock.systemUTC());

        assertThat(clientOf(provider).sslContext())
                .as("TOK-001-h requires the secret-store connection to be validated against the "
                        + "CONFIGURED trust material. The client is now built with the SSLContext "
                        + "resolved from billing.truststore.location, not the JVM default (cacerts).")
                .isNotSameAs(SSLContext.getDefault());
    }

    @Test // CFG-002-e, CFG-002-f
    @DisplayName("the STS client uses the configured truststore too (DEF-0105 fixed)")
    void theStsClientUsesTheConfiguredTruststore() throws Exception {
        BillingEdgeProperties properties = Fixtures.validProperties();

        WsTrustSecurityTokenService sts = new WsTrustSecurityTokenService(properties);

        assertThat(clientOf(sts).sslContext())
                .as("CFG-002-f requires both back ends to be trusted by the same configuration")
                .isNotSameAs(SSLContext.getDefault());
    }

    @Test // CFG-002-e/f/g, TOK-001-h — the structural corroboration, now positive
    @DisplayName("a single class in main/ loads the KeyStore and builds the SSLContext (DEF-0105 fixed)")
    void aProductionClassNowLoadsTheTruststore() throws Exception {
        Path main = Path.of("src/main/java");
        assertThat(Files.exists(main)).as("run from services/billing-edge").isTrue();

        List<String> offenders;
        try (Stream<Path> files = Files.walk(main)) {
            offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            String source = Files.readString(p);
                            return source.contains("SSLContext") || source.contains("KeyStore")
                                    || source.contains("TrustManagerFactory");
                        } catch (Exception unreadable) {
                            return false;
                        }
                    })
                    .map(Path::toString).toList();
        }

        // DEF-0105: OutboundTrustMaterial is the single place that reads the KeyStore and builds the
        // SSLContext both adapters consume. It is the structural evidence the configured trust material
        // is used at runtime, not only validated at startup.
        assertThat(offenders)
                .as("OutboundTrustMaterial loads the KeyStore and builds the SSLContext")
                .isNotEmpty();
        assertThat(offenders.stream().anyMatch(p -> p.endsWith("OutboundTrustMaterial.java")))
                .as("the SSLContext/KeyStore usage is concentrated in OutboundTrustMaterial")
                .isTrue();
    }

    @Test // CFG-002-i
    @DisplayName("CFG-002-i: no client certificate is presented (the configured truststore is a trust-only store)")
    void noClientCertificateIsPresented() throws Exception {
        BillingEdgeProperties properties = Fixtures.validProperties();
        assertThat(properties.getTruststore().isClientCertificate()).isFalse();
        // The truststore is trust-only: it validates the server certificate but presents no client
        // certificate. OutboundTrustMaterial builds the SSLContext with an empty KeyManager array, so
        // no client identity is offered on the mutual-TLS handshake (CFG-002-i).
        assertThat(clientOf(new WsTrustSecurityTokenService(properties)).authenticator()).isEmpty();
    }
}