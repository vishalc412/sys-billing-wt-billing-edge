package com.westfield.api.billing.edge.application.port;

import com.westfield.api.billing.platform.observability.MigratedFrom;

import java.util.Arrays;

/**
 * The billing service-account credentials, held for exactly as long as the token exchange needs them
 * (N-0072, ADR-0008).
 *
 * <p>The legacy places them in ordinary flow variables that persist for the rest of the request and
 * cross the flow-ref boundary back into the calling flow, so any later processor that dumped all
 * variables would print them (N-0071 ec 3, N-0072 ec 4). That is corrected here with no
 * consumer-visible effect: the value is mutable, is cleared after use, and cannot be serialised or
 * logged by accident — {@link #toString()} is redacted on purpose.
 */
@MigratedFrom(value = "km:node/N-0072", note = "thycotic-secret::billing.username/password; ADR-0008 lifetime")
public final class ServiceAccountCredentials implements AutoCloseable {

    private final String username;
    private final char[] password;

    public ServiceAccountCredentials(String username, char[] password) {
        this.username = username;
        this.password = password == null ? new char[0] : password.clone();
    }

    public String username() {
        return username;
    }

    public char[] password() {
        return password.clone();
    }

    /** True once {@link #close()} has overwritten the password material. */
    public boolean isCleared() {
        for (char c : password) {
            if (c != '\0') {
                return false;
            }
        }
        return true;
    }

    /** Overwrites the password in place. Called as soon as the exchange has finished. */
    @Override
    public void close() {
        Arrays.fill(password, '\0');
    }

    /** Never the value. A credential that can be logged eventually is (ADR-0008, ADR-0015). */
    @Override
    public String toString() {
        return "ServiceAccountCredentials[username=<redacted>, password=<redacted>]";
    }
}
