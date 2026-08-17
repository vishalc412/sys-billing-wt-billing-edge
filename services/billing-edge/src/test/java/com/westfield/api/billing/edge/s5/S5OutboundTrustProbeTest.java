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
 * DEF-0105 — the configured trust material is never loaded by anything.
 *
 * <p>CFG-002-e asserts that "the server certificate is validated against the configured trust material
 * and an untrusted certificate is refused"; CFG-002-f that both back ends are trusted by the same
 * configuration; CFG-002-g that a rotation takes effect without a redeploy; TOK-001-h the same for the
 * secret store. ADR-0041 goes further and requires the Thycotic client to be given the resolved
 * truststore password.
 *
 * <p>The S4 evidence for all of these is {@code EnvironmentProfileTest} and
 * {@code StartupConfigurationValidatorTest}, which assert that {@code billing.truststore.location} and
 * {@code billing.truststore.password} are declared in every profile and are not blank. That is the
 * configuration half, and the S4 test says so in a comment. This test asks the runtime half: is the
 * material actually used?
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
    @DisplayName("the Thycotic client uses the JVM DEFAULT SSLContext, not billing.truststore.location")
    void theVaultClientIgnoresTheConfiguredTruststore() throws Exception {
        BillingEdgeProperties properties = Fixtures.validProperties();
        properties.getTruststore().setLocation("classpath:truststore/does-not-exist-anywhere.jks");
        properties.getTruststore().setPassword("a-resolved-per-environment-password");

        ThycoticCredentialProvider provider =
                new ThycoticCredentialProvider(properties, new ObjectMapper(), Clock.systemUTC());

        assertThat(clientOf(provider).sslContext())
                .as("TOK-001-h requires the secret-store connection to be validated against the "
                        + "CONFIGURED trust material. The client was built with no SSLContext, so it "
                        + "uses the JVM default (cacerts) and a truststore location pointing at a file "
                        + "that does not exist changes nothing.")
                .isSameAs(SSLContext.getDefault());
    }

    @Test // CFG-002-e, CFG-002-f
    @DisplayName("the STS client uses the JVM DEFAULT SSLContext too")
    void theStsClientIgnoresTheConfiguredTruststore() throws Exception {
        BillingEdgeProperties properties = Fixtures.validProperties();

        WsTrustSecurityTokenService sts = new WsTrustSecurityTokenService(properties);

        assertThat(clientOf(sts).sslContext()).isSameAs(SSLContext.getDefault());
    }

    @Test // CFG-002-e/f/g, TOK-001-h — the structural corroboration
    @DisplayName("no class in main/ loads a KeyStore or builds an SSLContext at all")
    void noProductionClassEverLoadsTheTruststore() throws Exception {
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

        assertThat(offenders)
                .as("CFG-002-e/f/g and TOK-001-h all describe runtime behaviour over TLS. Nothing in "
                        + "the module constructs an SSLContext or reads a KeyStore, so billing.truststore.* "
                        + "is validated at startup, printed in a log line, and used by nothing. ADR-0041 "
                        + "(give the Thycotic client the resolved truststore password) is unimplemented.")
                .isEmpty();
    }

    @Test // CFG-002-i
    @DisplayName("CFG-002-i: no client certificate is presented (true, but for the same reason: no SSL config exists)")
    void noClientCertificateIsPresented() throws Exception {
        BillingEdgeProperties properties = Fixtures.validProperties();
        assertThat(properties.getTruststore().isClientCertificate()).isFalse();
        // The observable outcome the criterion asks for holds; it holds because no SSL configuration
        // is performed at all, not because a decision was implemented. Recorded so the pass is not
        // mistaken for evidence that the trust configuration works.
        assertThat(clientOf(new WsTrustSecurityTokenService(properties)).authenticator()).isEmpty();
    }
}
