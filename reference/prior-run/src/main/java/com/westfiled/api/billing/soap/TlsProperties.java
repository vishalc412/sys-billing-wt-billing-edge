package com.westfiled.api.billing.soap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Maps to truststore.path / truststore.password (Mule's TLS_Context_Request) used to validate the
 * downstream Billing/Ping server certificates. When truststorePath is blank the JDK default trust
 * store is used instead (e.g. local development against a publicly-trusted endpoint).
 */
@ConfigurationProperties(prefix = "app.tls")
public class TlsProperties {

    private String truststorePath;
    private String truststorePassword;

    public String getTruststorePath() {
        return truststorePath;
    }

    public void setTruststorePath(String truststorePath) {
        this.truststorePath = truststorePath;
    }

    public String getTruststorePassword() {
        return truststorePassword;
    }

    public void setTruststorePassword(String truststorePassword) {
        this.truststorePassword = truststorePassword;
    }
}
