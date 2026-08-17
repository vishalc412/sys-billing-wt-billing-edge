package com.westfield.api.billing.edge.config;

import com.westfield.api.billing.edge.config.StartupConfigurationValidator.ConfigurationValidationException;
import com.westfield.api.billing.edge.testsupport.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CFG-001 and CFG-002 at the moment of startup (ADR-0018, ADR-0039, ADR-0041).
 *
 * <p>Everything asserted here happens before the first request, so none of it is observable to a
 * consumer — which is exactly why the corrections are free to make and why refusing to start is the
 * right failure mode. The legacy's behaviour, which these tests exist to replace:
 * <ul>
 *   <li>{@code mule.env} defaults to {@code dev}, so an application deployed without it runs happily
 *       against DEVELOPMENT back ends and nothing says so (N-0002);</li>
 *   <li>four required properties exist in no file in the repository, so they fail on the first
 *       request that needs them rather than at deploy time (N-0003 ec 1, R-014);</li>
 *   <li>the truststore password is referenced unprefixed in one place and prefixed in another, so
 *       one of the two probably resolves to nothing or to raw ciphertext and nobody can say which
 *       without a runtime (N-0008 defect, ADR-0041).</li>
 * </ul>
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("CFG-001 / CFG-002 startup configuration validation")
class StartupConfigurationValidatorTest {

    private static StartupConfigurationValidator validatorFor(BillingEdgeProperties properties) {
        return new StartupConfigurationValidator(properties);
    }

    @Test // CFG-001-a
    @DisplayName("a: with no environment selected the service refuses to start rather than binding to dev")
    void refusesToStartWithNoEnvironmentSelected() {
        StartupConfigurationValidator validator = validatorFor(Fixtures.validProperties());

        // The scaffold placeholder is what an unset profile looks like. It is not an environment.
        assertThatThrownBy(() -> validator.validate(List.of(StartupConfigurationValidator.NO_PROFILE_SELECTED)))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessageContaining("No environment selected")
                // The message must name the alternative. "Configuration invalid" costs an hour at
                // cutover; naming the profiles costs nothing.
                .hasMessageContaining("local")
                .hasMessageContaining("prod");

        assertThatThrownBy(() -> validator.validate(List.of()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessageContaining("No environment selected");
    }

    @Test // CFG-001-b
    @DisplayName("b: an unknown environment name fails the start naming the missing configuration set")
    void unknownEnvironmentNameFailsRatherThanFallingBack() {
        StartupConfigurationValidator validator = validatorFor(Fixtures.validProperties());

        // A typo is the case that matters: 'prd' must not quietly become 'dev'. The legacy would
        // have run against development back ends and said nothing at all.
        assertThatThrownBy(() -> validator.validate(List.of("prd")))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessageContaining("prd")
                .hasMessageContaining("application-<environment>.yaml");
    }

    @Test
    @DisplayName("a valid environment with a complete configuration starts")
    void aCompleteConfigurationStarts() {
        // CFG-001-c (the per-environment VALUES) is asserted against the profile files themselves in
        // EnvironmentProfileTest. What is asserted here is only that a complete set passes the gate,
        // so that the failure tests above are known to be failing for the reason stated rather than
        // because the fixture is broken.
        assertThatCode(() -> validatorFor(Fixtures.validProperties()).validate(List.of("test")))
                .doesNotThrowAnyException();
    }

    @Test // CFG-001-d
    @DisplayName("d: a deployment-time value that is absent fails the start naming that value")
    void anAbsentDeploymentValueFailsTheStartByName() {
        // N-0003 ec 1: these four arrive only at deployment time and exist in no file in the legacy
        // repository. Each one is named individually, because "some configuration is missing" is not
        // actionable at 2am.
        assertThatThrownBy(() -> {
            BillingEdgeProperties properties = Fixtures.validProperties();
            properties.getBackend().getEsb().setHost(null);
            validatorFor(properties).validate(List.of("test"));
        }).isInstanceOf(ConfigurationValidationException.class)
                .hasMessageContaining("billing.backend.esb.host")
                .hasMessageContaining("startup fails here rather than on the first request");

        assertThatThrownBy(() -> {
            BillingEdgeProperties properties = Fixtures.validProperties();
            properties.getApi().setId(null);
            validatorFor(properties).validate(List.of("test"));
        }).isInstanceOf(ConfigurationValidationException.class)
                .hasMessageContaining("billing.api.id");

        assertThatThrownBy(() -> {
            BillingEdgeProperties properties = Fixtures.validProperties();
            properties.getSaml().setAudience(null);
            validatorFor(properties).validate(List.of("test"));
        }).isInstanceOf(ConfigurationValidationException.class)
                .hasMessageContaining("billing.saml.audience");

        // The unreferenced property is asserted too, so that "referenced by nothing" cannot become
        // "quietly deleted" (ADR-0018 decision 5, R-022).
        assertThatThrownBy(() -> {
            BillingEdgeProperties properties = Fixtures.validProperties();
            properties.setPasmIdBilling(null);
            validatorFor(properties).validate(List.of("test"));
        }).isInstanceOf(ConfigurationValidationException.class)
                .hasMessageContaining("billing.pasm-id-billing");
    }

    @Test // CFG-001-g
    @DisplayName("g: stage pointing at the TEST ESB is announced at startup and deliberately not corrected")
    void stagePointingAtTheTestEsbIsAnnouncedNotCorrected() {
        BillingEdgeProperties properties = Fixtures.validProperties();
        properties.getBackend().getEsb().setHost("https://tst2esb.westfieldgrp.com");

        // Startup must SUCCEED. Nobody knows whether a stage ESB exists at all (R-023), so failing
        // the start — or silently rewriting the host — could break stage outright. The value is
        // carried forward verbatim and made impossible to inherit unknowingly.
        assertThatCode(() -> validatorFor(properties).validate(List.of("stage")))
                .doesNotThrowAnyException();

        assertThat(properties.getBackend().getEsb().getHost())
                .as("the legacy stage value is preserved exactly (N-0101 ec 2)")
                .isEqualTo("https://tst2esb.westfieldgrp.com");
    }

    @Test // CFG-002-b
    @DisplayName("b: an absent secret-store credential fails the start naming it, and no request is served")
    void anAbsentSecretFailsTheStart() {
        assertThatThrownBy(() -> {
            BillingEdgeProperties properties = Fixtures.validProperties();
            properties.getTruststore().setPassword(null);
            validatorFor(properties).validate(List.of("test"));
        }).isInstanceOf(ConfigurationValidationException.class)
                .hasMessageContaining("billing.truststore.password");

        assertThatThrownBy(() -> {
            BillingEdgeProperties properties = Fixtures.validProperties();
            properties.getTruststore().setLocation(null);
            validatorFor(properties).validate(List.of("test"));
        }).isInstanceOf(ConfigurationValidationException.class)
                .hasMessageContaining("billing.truststore.location");
    }

    @Test // CFG-002-d
    @DisplayName("d: a secret reference that did not resolve fails the start instead of becoming an unusable value")
    void anUnresolvedSecretReferenceFailsTheStart() {
        // This is ADR-0041 made mechanical. The legacy writes the truststore password with the
        // secure:: prefix in one place and without it in another; one of those references resolves
        // to the literal placeholder or to raw ciphertext, and the failure only shows up as an
        // unexplained TLS error on the first outbound call.
        assertThatThrownBy(() -> {
            BillingEdgeProperties properties = Fixtures.validProperties();
            properties.getTruststore().setPassword("${BILLING_TRUSTSTORE_PASSWORD}");
            validatorFor(properties).validate(List.of("test"));
        }).isInstanceOf(ConfigurationValidationException.class)
                .hasMessageContaining("did not resolve");

        // The legacy ciphertext form. ADR-0039 forbids it being carried forward in ANY form, so
        // recognising it and refusing is the check that makes the prohibition real.
        assertThatThrownBy(() -> {
            BillingEdgeProperties properties = Fixtures.validProperties();
            properties.getTruststore().setPassword("![qkKr7VGZlKQ0nIQ2NBK3Sw==]");
            validatorFor(properties).validate(List.of("test"));
        }).isInstanceOf(ConfigurationValidationException.class)
                .hasMessageContaining("ADR-0039");
    }

    @Test // CFG-002-h
    @DisplayName("h: the outbound TLS posture is stated at startup rather than left as a runtime default")
    void outboundTlsPostureIsStatedExplicitly() {
        // The legacy restricts neither protocol nor cipher (N-0014 ec 2), so the effective policy is
        // whatever the JVM happens to do that week. This does not PIN a protocol — pinning one
        // without measurement could break a back end — it makes R-016 observable so the decision can
        // be taken on evidence.
        String description = validatorFor(Fixtures.validProperties()).describeOutboundTls();

        assertThat(description).contains("protocols=");
        assertThat(description).contains("clientCertificate=false");
        assertThat(description).contains("truststore=");
        assertThat(description)
                .as("one trust context serves both back ends, as today (N-0014 ec 1)")
                .contains("one trust context for both back ends");
    }

    @Test // CFG-002-i
    @DisplayName("i: presenting a client certificate is a security-model change and fails the start")
    void noClientCertificateIsEverPresented() {
        // N-0014: all trust rests on the assertion inside the message, not on the transport. Turning
        // mutual TLS on would be a change to the security model that looks like a hardening tweak,
        // so it is refused here rather than left to a configuration typo.
        BillingEdgeProperties properties = Fixtures.validProperties();
        assertThat(properties.getTruststore().isClientCertificate())
                .as("the default is no client certificate, matching the legacy")
                .isFalse();

        properties.getTruststore().setClientCertificate(true);
        assertThatThrownBy(() -> validatorFor(properties).validate(List.of("test")))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessageContaining("security-model change");
    }

    @Test // CFG-002-e
    @DisplayName("e: trust material is required configuration, so an outbound call cannot fall back to no validation")
    void trustMaterialIsRequiredSoValidationCannotBeSkipped() {
        // The runtime half of this criterion — an untrusted certificate being refused — is the JDK's
        // default behaviour, and EnvironmentProfileTest proves the code never installs a permissive
        // TrustManager that would remove it. What is asserted here is the configuration half: there is no
        // path to starting without trust material, so "validation was accidentally off" is not a
        // reachable state.
        BillingEdgeProperties properties = Fixtures.validProperties();
        properties.getTruststore().setLocation("   ");

        assertThatThrownBy(() -> validatorFor(properties).validate(List.of("test")))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessageContaining("billing.truststore.location");
    }

    @Test
    @DisplayName("an entitlement exemption without an owner and an expiry fails the start (ADR-0037)")
    void exemptionsNeedAnOwnerAndAnExpiry() {
        // Not a numbered criterion, but it is the mechanism that stops ADR-0037's exemption list
        // becoming an unowned hole, so it is asserted rather than assumed.
        BillingEdgeProperties properties = Fixtures.validProperties();
        properties.getSecurity().getAgencyEntitlementExemptClients()
                .add(Fixtures.exemption("batch-client", null, LocalDate.of(2027, 1, 1)));

        assertThatThrownBy(() -> validatorFor(properties).validate(List.of("test")))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessageContaining("named owner");

        properties.getSecurity().getAgencyEntitlementExemptClients().clear();
        properties.getSecurity().getAgencyEntitlementExemptClients()
                .add(Fixtures.exemption("batch-client", "claims-platform-team", null));

        assertThatThrownBy(() -> validatorFor(properties).validate(List.of("test")))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessageContaining("expiry");
    }
}
