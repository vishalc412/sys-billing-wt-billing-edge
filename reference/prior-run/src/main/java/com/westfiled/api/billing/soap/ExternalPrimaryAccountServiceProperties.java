package com.westfiled.api.billing.soap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Maps to externalPrimaryAccount.host / response.timeout in the Mule properties files. Backs the
 * SOAP endpoint (Mule's global HTTP_Request_configuration, path /Billing/BillingService) used by
 * the externalPrimaryAccount, pastDueToday and pendingCancelToday flows.
 */
@ConfigurationProperties(prefix = "app.external-primary-account")
public class ExternalPrimaryAccountServiceProperties {

    private String scheme = "https";
    private String host;
    private int port = 443;
    private String billingServicePath = "/Billing/BillingService";
    private long timeoutMs = 10000;

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getBillingServicePath() {
        return billingServicePath;
    }

    public void setBillingServicePath(String billingServicePath) {
        this.billingServicePath = billingServicePath;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
