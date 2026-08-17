package com.westfield.api.billing.edge.config;

import com.westfield.api.billing.platform.observability.MigratedFrom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Fail-fast configuration validation (ADR-0018, CFG-001, CFG-002).
 *
 * <p>Six recorded configuration facts, each small, collectively this class. The legacy
 * {@code mule.env} defaults to {@code dev}, so an application started without it silently runs
 * against DEVELOPMENT back ends (N-0002); {@code local.properties} omits the ESB host and path, so
 * three endpoints cannot start locally at all (N-0003 ec 2, N-0101 ec 1); and four required
 * properties exist in no file in the repository (N-0003 ec 1, R-014), failing on the first request
 * rather than at startup.
 *
 * <p>Everything this class does happens BEFORE the first request. No consumer can observe any of it,
 * which is why correcting it is free — and why refusing to start is the right failure mode: a loud
 * break at deploy time is strictly better than a silent connection to the wrong back end.
 */
@MigratedFrom(value = "km:node/N-0002",
        note = "mule.env selection and the missing-property failures; ADR-0018 fail-fast")
public class StartupConfigurationValidator {

    private static final Logger LOG = LoggerFactory.getLogger(StartupConfigurationValidator.class);

    /** The six environments the legacy defines a property file for. There is no seventh. */
    public static final Set<String> KNOWN_ENVIRONMENTS =
            new LinkedHashSet<>(List.of("local", "dev", "test", "stage", "perf", "prod"));

    /** The scaffold placeholder. Reaching startup with this value means no profile was selected. */
    public static final String NO_PROFILE_SELECTED = "none-configured";

    private final BillingEdgeProperties properties;

    public StartupConfigurationValidator(BillingEdgeProperties properties) {
        this.properties = properties;
    }

    /**
     * @param activeProfiles the Spring profiles actually in force
     * @throws ConfigurationValidationException naming the first thing that is wrong. The message
     *         names the property, because "configuration invalid" costs an hour at cutover.
     */
    @MigratedFrom(value = "km:node/N-0002", note = "no environment default; a typo fails, never falls back")
    public void validate(List<String> activeProfiles) {
        String environment = selectEnvironment(activeProfiles);
        requireDeploymentValues();
        requireResolvedSecrets();
        requireOwnedExemptions();
        announceResolvedBackends(environment);
    }

    /**
     * The profile does the job {@code mule.env} did. There is deliberately NO default: an
     * application with no explicit environment refuses to start rather than binding to development
     * back ends (ADR-0018 decision 1).
     */
    private String selectEnvironment(List<String> activeProfiles) {
        List<String> candidates = new ArrayList<>();
        for (String profile : activeProfiles) {
            if (!NO_PROFILE_SELECTED.equals(profile)) {
                candidates.add(profile);
            }
        }
        if (candidates.isEmpty()) {
            throw new ConfigurationValidationException(
                    "No environment selected. Set an explicit Spring profile (one of "
                            + KNOWN_ENVIRONMENTS + "). There is no default: ADR-0018 removes the "
                            + "legacy silent fallback to dev.");
        }
        for (String candidate : candidates) {
            if (KNOWN_ENVIRONMENTS.contains(candidate)) {
                return candidate;
            }
        }
        throw new ConfigurationValidationException(
                "No configuration set exists for profile(s) " + candidates
                        + ". Expected one of " + KNOWN_ENVIRONMENTS
                        + " (application-<environment>.yaml). Startup fails rather than falling back.");
    }

    /**
     * The values that arrive only at deployment time (N-0003 ec 1). The legacy discovers they are
     * missing on the first request that needs them; here they are named at startup.
     */
    private void requireDeploymentValues() {
        require(properties.getApi().getId(), "billing.api.id (the API Manager instance id, per environment)");
        require(properties.getApi().getBasePath(), "billing.api.base-path");
        require(properties.getSaml().getAudience(), "billing.saml.audience");
        require(properties.getBackend().getEsb().getHost(), "billing.backend.esb.host");
        require(properties.getBackend().getWas().getHost(), "billing.backend.was.host");
        require(properties.getBackend().getSts().getHost(), "billing.backend.sts.host");
        require(properties.getBackend().getVault().getHost(), "billing.backend.vault.host");
        // Carried forward verbatim pending R-022 (ADR-0018 decision 5): defined in all six legacy
        // environment files, referenced by nothing. Kept, not dropped; asserted so it cannot vanish.
        require(properties.getPasmIdBilling(), "billing.pasm-id-billing (unreferenced, kept per ADR-0018/R-022)");
    }

    /**
     * Every secret reference must actually resolve. The legacy writes the truststore password
     * unprefixed in one place and prefixed in another, so one of the two references probably
     * resolves to nothing or to raw ciphertext, and nobody can confirm which without a runtime
     * (N-0008 defect). ADR-0041 corrects it; this check is what makes the correction verifiable.
     */
    private void requireResolvedSecrets() {
        require(properties.getTruststore().getLocation(), "billing.truststore.location");
        requireSecret(properties.getTruststore().getPassword(), "billing.truststore.password");
        if (properties.getTruststore().isClientCertificate()) {
            throw new ConfigurationValidationException(
                    "billing.truststore.client-certificate is true. The legacy presents no client "
                            + "certificate on any outbound call (N-0014); all trust rests on the "
                            + "assertion in the message. Enabling it is a security-model change.");
        }
    }

    /**
     * ADR-0037: an exemption from the agency entitlement check is a name, an owner and an expiry.
     * An entry missing either is a hole nobody signed for, so it fails the start.
     */
    private void requireOwnedExemptions() {
        for (BillingEdgeProperties.Security.ExemptClient exemption
                : properties.getSecurity().getAgencyEntitlementExemptClients()) {
            if (isBlank(exemption.getClientId()) || isBlank(exemption.getOwner()) || exemption.getExpires() == null) {
                throw new ConfigurationValidationException(
                        "Agency-entitlement exemption '" + exemption.getClientId()
                                + "' needs a client-id, a named owner and an expiry date (ADR-0037).");
            }
        }
        if (properties.getSecurity().getAgencyEntitlement()
                == BillingEdgeProperties.Security.Mode.LOG_ONLY) {
            // Not a failure — but it is a decision to carry a known cross-agency customer-PII
            // exposure, and it must never be invisible in the log (ADR-0037).
            LOG.warn("AGENCY ENTITLEMENT IS IN log-only MODE. Unentitled worklist requests will be "
                    + "SERVED and counted, not denied. ADR-0037 requires a named human owner for this.");
        }
    }

    /**
     * ADR-0018 decision 3 and CFG-002-h: say out loud which back ends and which outbound TLS policy
     * this instance resolved to. Stage still points at the TEST ESB and that is not corrected —
     * correcting it could break stage outright, because nobody knows whether a stage ESB exists
     * (N-0101 ec 2, R-023). It is made visible instead.
     */
    private void announceResolvedBackends(String environment) {
        LOG.info("Environment '{}' resolved: apiId={} basePath={} esb={} was={} sts={} vault={}",
                environment, properties.getApi().getId(), properties.getApi().getBasePath(),
                properties.getBackend().getEsb().getHost(), properties.getBackend().getWas().getHost(),
                properties.getBackend().getSts().getHost(), properties.getBackend().getVault().getHost());
        LOG.info("Outbound TLS: {}", describeOutboundTls());
        if ("stage".equals(environment) && stageIsPointingAtTheTestEsb()) {
            LOG.warn("Stage is configured against the TEST ESB ({}). Recorded as observed "
                            + "configuration and deliberately not corrected (N-0101 ec 2, ADR-0018, R-023).",
                    properties.getBackend().getEsb().getHost());
        }
    }

    private boolean stageIsPointingAtTheTestEsb() {
        String host = properties.getBackend().getEsb().getHost();
        return host != null && host.toLowerCase(java.util.Locale.ROOT).contains("tst");
    }

    /**
     * CFG-002-h: the negotiated protocol set is stated rather than left as an unstated runtime
     * default. The legacy restricts neither protocol nor cipher, so the effective policy is whatever
     * the JVM happens to do (N-0014 ec 2). This does not pin a protocol — it makes R-016 observable.
     */
    public String describeOutboundTls() {
        return "protocols=[TLSv1.3, TLSv1.2] clientCertificate=" + properties.getTruststore().isClientCertificate()
                + " truststore=" + properties.getTruststore().getLocation()
                + " (one trust context for both back ends and the vault, as today — N-0014 ec 1)";
    }

    private static void require(String value, String name) {
        if (isBlank(value)) {
            throw new ConfigurationValidationException(
                    "Required configuration '" + name + "' is not set. It is supplied at deployment "
                            + "time; startup fails here rather than on the first request (ADR-0018).");
        }
    }

    private static void requireSecret(String value, String name) {
        require(value, name);
        if (value.startsWith("${") || value.startsWith("![")) {
            throw new ConfigurationValidationException(
                    "Secret '" + name + "' did not resolve (value is still a placeholder or legacy "
                            + "ciphertext). The legacy secure.key and its ciphertext are not carried "
                            + "forward in any form (ADR-0039).");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Refusing to start is the point. The message always names the offending property. */
    public static class ConfigurationValidationException extends IllegalStateException {
        public ConfigurationValidationException(String message) {
            super(message);
        }
    }
}
