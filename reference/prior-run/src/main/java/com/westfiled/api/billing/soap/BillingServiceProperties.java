package com.westfiled.api.billing.soap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Maps to primaryAccount.host / primaryAccount.path / response.timeout in the Mule
 * dev/test/stage/perf/prod.properties files. Backs the SOAP endpoint used by the
 * primaryAccount, primaryAccount/transactions and primaryAccount/policy/escrow/transactions
 * flows (Mule's HTTP_Request_config_PrimaryAccount).
 */
@ConfigurationProperties(prefix = "app.primary-account")
public class BillingServiceProperties {

    private String scheme = "https";
    private String host;
    private int port = 443;
    private String path;
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

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
