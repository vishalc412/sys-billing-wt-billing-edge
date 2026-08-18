package com.westfield.api.billing.edge.config;

import com.westfield.api.billing.platform.observability.MigratedFrom;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything that varies by environment, in one typed object (N-0003, N-0101).
 *
 * <p>The legacy externalises the same set into one properties file per environment, selected by
 * {@code mule.env} — which selects three things at once: the plain properties, the secure properties
 * and the Thycotic configuration (N-0002 ec). Here the Spring profile plays that role, and
 * {@link StartupConfigurationValidator} refuses to start when it is not set, because the legacy's
 * silent default to {@code dev} is exactly the failure ADR-0018 removes.
 *
 * <p>Values migrate verbatim, environment for environment. Two recorded oddities are carried
 * forward on purpose and are visible here: {@code pasmIdBilling} is referenced by nothing and is
 * kept pending R-022, and the stage ESB host still points at the test ESB (N-0101 ec 2), flagged by
 * a startup WARN rather than corrected, because nobody knows whether a stage ESB exists (R-023).
 */
@ConfigurationProperties(prefix = "billing")
@MigratedFrom(value = "km:node/N-0101", note = "the six environment property files as one typed object")
public class BillingEdgeProperties {

    private Api api = new Api();
    private Security security = new Security();
    private Saml saml = new Saml();
    private Credentials credentials = new Credentials();
    private Console console = new Console();
    private Backend backend = new Backend();
    private Truststore truststore = new Truststore();
    private Logging logging = new Logging();

    /**
     * Defined in all six legacy environment files and referenced by nothing anywhere (N-0101 ec 4).
     * ADR-0018 carries it forward verbatim rather than dropping it: a property defined six times and
     * used nowhere is the shape of something an external process greps for, it costs nothing to
     * keep, and dropping it wrongly is irreversible. R-022 remains open.
     */
    private String pasmIdBilling;

    public Api getApi() {
        return api;
    }

    public void setApi(Api api) {
        this.api = api;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Saml getSaml() {
        return saml;
    }

    public void setSaml(Saml saml) {
        this.saml = saml;
    }

    public Credentials getCredentials() {
        return credentials;
    }

    public void setCredentials(Credentials credentials) {
        this.credentials = credentials;
    }

    public Console getConsole() {
        return console;
    }

    public void setConsole(Console console) {
        this.console = console;
    }

    public Backend getBackend() {
        return backend;
    }

    public void setBackend(Backend backend) {
        this.backend = backend;
    }

    public Truststore getTruststore() {
        return truststore;
    }

    public void setTruststore(Truststore truststore) {
        this.truststore = truststore;
    }

    public Logging getLogging() {
        return logging;
    }

    public void setLogging(Logging logging) {
        this.logging = logging;
    }

    public String getPasmIdBilling() {
        return pasmIdBilling;
    }

    public void setPasmIdBilling(String pasmIdBilling) {
        this.pasmIdBilling = pasmIdBilling;
    }

    /** API identity and the externally visible base path (N-0004, N-0022 ec 1). */
    public static class Api {
        /** The API Manager instance id, different in every environment (N-0004 ec 1). */
        private String id;
        private String name = "sapi-billing";
        private String version = "v1";
        /**
         * The listener path. '/*' in every deployed environment and 'sapi-billing/v1/*' only in
         * local, so the externally visible base path comes from infrastructure, not from the
         * artifact (N-0022 ec 1, U12/R-002).
         */
        private String basePath = "/";

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getBasePath() {
            return basePath;
        }

        public void setBasePath(String basePath) {
            this.basePath = basePath;
        }
    }

    /** ADR-0037. Enforcement is on by default in every profile, including production. */
    public static class Security {
        private Mode agencyEntitlement = Mode.ENFORCE;
        private List<ExemptClient> agencyEntitlementExemptClients = new ArrayList<>();

        public Mode getAgencyEntitlement() {
            return agencyEntitlement;
        }

        public void setAgencyEntitlement(Mode agencyEntitlement) {
            this.agencyEntitlement = agencyEntitlement;
        }

        public List<ExemptClient> getAgencyEntitlementExemptClients() {
            return agencyEntitlementExemptClients;
        }

        public void setAgencyEntitlementExemptClients(List<ExemptClient> exemptClients) {
            this.agencyEntitlementExemptClients = exemptClients;
        }

        public enum Mode {
            /** Deny an unentitled request with 403. The default, everywhere. */
            ENFORCE,
            /**
             * Serve it and count it. Selecting this is a decision to carry a known cross-agency
             * customer-PII exposure into the new system and requires a named human owner recorded
             * in ADR-0037. It is not reachable by an engineer's choice.
             */
            LOG_ONLY
        }

        /** An exemption without an owner and an expiry fails startup (ADR-0037). */
        public static class ExemptClient {
            private String clientId;
            private String owner;
            private LocalDate expires;

            public String getClientId() {
                return clientId;
            }

            public void setClientId(String clientId) {
                this.clientId = clientId;
            }

            public String getOwner() {
                return owner;
            }

            public void setOwner(String owner) {
                this.owner = owner;
            }

            public LocalDate getExpires() {
                return expires;
            }

            public void setExpires(LocalDate expires) {
                this.expires = expires;
            }
        }
    }

    /** ADR-0007. Zero TTL is the cutover default: mint per request under either legacy behaviour. */
    public static class Saml {
        private long cacheTtlSeconds = 0;
        /**
         * The audience restriction. This, and only this, is what stops a non-production assertion
         * being accepted by the production billing service (N-0019).
         */
        private String audience;

        public long getCacheTtlSeconds() {
            return cacheTtlSeconds;
        }

        public void setCacheTtlSeconds(long cacheTtlSeconds) {
            this.cacheTtlSeconds = cacheTtlSeconds;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }
    }

    /** ADR-0008. Exactly two secret names are consulted, the same two the legacy used (N-0008 ec 3). */
    public static class Credentials {
        private long cacheTtlSeconds = 60;
        private String usernameSecretName = "billing.username";
        private String passwordSecretName = "billing.password";

        public long getCacheTtlSeconds() {
            return cacheTtlSeconds;
        }

        public void setCacheTtlSeconds(long cacheTtlSeconds) {
            this.cacheTtlSeconds = cacheTtlSeconds;
        }

        public String getUsernameSecretName() {
            return usernameSecretName;
        }

        public void setUsernameSecretName(String usernameSecretName) {
            this.usernameSecretName = usernameSecretName;
        }

        public String getPasswordSecretName() {
            return passwordSecretName;
        }

        public void setPasswordSecretName(String passwordSecretName) {
            this.passwordSecretName = passwordSecretName;
        }
    }

    /** ADR-0038. Path and enablement are configuration; disabled in the production profile. */
    public static class Console {
        private boolean enabled = true;
        private String path = "/console";
        /**
         * The published contract this console browses. Packaged into the artifact and resolved from
         * the classpath (DEF-0103 / CON-001-a), so the console serves the contract in any environment
         * regardless of the process working directory. Production overrides enablement, not the
         * location.
         */
        private String contractLocation = "classpath:contracts/billing-edge/openapi.yaml";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getContractLocation() {
            return contractLocation;
        }

        public void setContractLocation(String contractLocation) {
            this.contractLocation = contractLocation;
        }
    }

    /** ADR-0014. Every timeout is an explicit number; none is inherited from a connector default. */
    public static class Backend {
        private Endpoint esb = new Endpoint();
        private Endpoint was = new Endpoint();
        private Endpoint sts = new Endpoint();
        private Endpoint vault = new Endpoint();

        public Endpoint getEsb() {
            return esb;
        }

        public void setEsb(Endpoint esb) {
            this.esb = esb;
        }

        public Endpoint getWas() {
            return was;
        }

        public void setWas(Endpoint was) {
            this.was = was;
        }

        public Endpoint getSts() {
            return sts;
        }

        public void setSts(Endpoint sts) {
            this.sts = sts;
        }

        public Endpoint getVault() {
            return vault;
        }

        public void setVault(Endpoint vault) {
            this.vault = vault;
        }

        public static class Endpoint {
            private String host;
            private java.time.Duration connectTimeout = java.time.Duration.ofSeconds(10);
            private java.time.Duration responseTimeout = java.time.Duration.ofSeconds(10);

            public String getHost() {
                return host;
            }

            public void setHost(String host) {
                this.host = host;
            }

            public java.time.Duration getConnectTimeout() {
                return connectTimeout;
            }

            public void setConnectTimeout(java.time.Duration connectTimeout) {
                this.connectTimeout = connectTimeout;
            }

            public java.time.Duration getResponseTimeout() {
                return responseTimeout;
            }

            public void setResponseTimeout(java.time.Duration responseTimeout) {
                this.responseTimeout = responseTimeout;
            }
        }
    }

    /**
     * Outbound trust (N-0014). One trust context serves both back ends and the vault, as today, and
     * no client certificate is presented: all trust rests on the assertion in the message.
     *
     * <p>The password is resolved per environment (ADR-0039) and is supplied as an environment
     * variable, never as a committed value. The legacy ships one byte-identical ciphertext across
     * all six environments and the decryption key is not in the repository at all (N-0102) — neither
     * the ciphertext nor the key is carried forward in any form.
     */
    public static class Truststore {
        private String location;
        private String password;
        private boolean clientCertificate = false;

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public boolean isClientCertificate() {
            return clientCertificate;
        }

        public void setClientCertificate(boolean clientCertificate) {
            this.clientCertificate = clientCertificate;
        }
    }

    /**
     * ADR-0015. The legacy defines both lists and leaves them EMPTY in every environment, so full
     * SOAP envelopes including the SAML assertion, and full fault payloads, are logged everywhere
     * (N-0009 ec 1, N-0101 ec 6). The target ships non-empty defaults; that is a deliberate
     * divergence on the logging path only, and no response body changes.
     */
    public static class Logging {
        private List<String> maskFields = new ArrayList<>(List.of(
                "assertion", "wsse:Security", "Security", "billingAccountNumber", "policyNumber",
                "agencyCode", "password", "userName"));
        private List<String> disableFields = new ArrayList<>();

        public List<String> getMaskFields() {
            return maskFields;
        }

        public void setMaskFields(List<String> maskFields) {
            this.maskFields = maskFields;
        }

        public List<String> getDisableFields() {
            return disableFields;
        }

        public void setDisableFields(List<String> disableFields) {
            this.disableFields = disableFields;
        }
    }
}
