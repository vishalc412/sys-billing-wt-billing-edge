package com.westfiled.api.billing.soap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Maps to ping.fed / ping.fed.port / saml.wsa.address in the Mule properties files, plus
 * thycotic-secret::billing.username / billing.password (secure-*.properties, Thycotic Secret
 * Server in the original Mule flow). Backs GetSAMLToken (module-pingfed:generate-saml).
 */
@ConfigurationProperties(prefix = "app.saml")
public class SamlProperties {

    private String scheme = "https";
    private String host;
    private int port = 9543;
    private String stsPath = "/idp/sts.wst";
    private String wsaAddress;
    private String username;
    private String password;
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

    public String getStsPath() {
        return stsPath;
    }

    public void setStsPath(String stsPath) {
        this.stsPath = stsPath;
    }

    public String getWsaAddress() {
        return wsaAddress;
    }

    public void setWsaAddress(String wsaAddress) {
        this.wsaAddress = wsaAddress;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
