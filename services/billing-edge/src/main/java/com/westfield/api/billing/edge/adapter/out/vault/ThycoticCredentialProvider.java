package com.westfield.api.billing.edge.adapter.out.vault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westfield.api.billing.edge.application.port.CredentialProvider;
import com.westfield.api.billing.edge.application.port.ServiceAccountCredentials;
import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.platform.observability.MigratedFrom;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reads the billing service-account credentials from Thycotic Secret Server (N-0008, N-0020, N-0072).
 *
 * <p>Exactly two secret names are consulted, and they are the two the legacy uses:
 * {@code billing.username} and {@code billing.password} (N-0008 ec 3). They are hardcoded in the
 * legacy, so renaming a secret in the vault breaks every backend call (N-0072 ec 1); here they are
 * configuration, which changes nothing observable and makes the coupling visible.
 *
 * <p>Cached for a short TTL (default 60s, ADR-0008). The legacy's caching behaviour is internal to
 * an Exchange module and cannot be determined from source (N-0008 ec 1, R-007), so this states its
 * own: a rotation still takes effect without a redeploy, and the vault call rate stays bounded.
 *
 * <p>There is deliberately no silent failure branch. The legacy has none either — the {@code p()}
 * call raises and the flow drops into its {@code on-error-continue}, so a vault outage reaches the
 * caller as a generic backend error (N-0072 ec 2). Here it reaches the log as what it is.
 */
@Component
@MigratedFrom(value = "km:node/N-0072", note = "thycotic-secret::billing.username/password; ADR-0008 TTL cache")
public class ThycoticCredentialProvider implements CredentialProvider {

    private final BillingEdgeProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final HttpClient httpClient;
    private final AtomicReference<CachedSecret> cachedUsername = new AtomicReference<>();
    private final AtomicReference<CachedSecret> cachedPassword = new AtomicReference<>();

    public ThycoticCredentialProvider(BillingEdgeProperties properties, ObjectMapper objectMapper, Clock clock) {
        this(properties, objectMapper, clock, HttpClient.newBuilder()
                .connectTimeout(properties.getBackend().getVault().getConnectTimeout())
                .build());
    }

    ThycoticCredentialProvider(BillingEdgeProperties properties,
                               ObjectMapper objectMapper,
                               Clock clock,
                               HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.httpClient = httpClient;
    }

    @Override
    @MigratedFrom(value = "km:node/N-0020", note = "rotation takes effect without a redeploy")
    public ServiceAccountCredentials serviceAccount() {
        String username = resolve(cachedUsername, properties.getCredentials().getUsernameSecretName());
        String password = resolve(cachedPassword, properties.getCredentials().getPasswordSecretName());
        return new ServiceAccountCredentials(username, password.toCharArray());
    }

    private String resolve(AtomicReference<CachedSecret> slot, String secretName) {
        CachedSecret cached = slot.get();
        Instant now = clock.instant();
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.value();
        }
        String value = fetch(secretName);
        slot.set(new CachedSecret(value,
                now.plusSeconds(properties.getCredentials().getCacheTtlSeconds())));
        return value;
    }

    private String fetch(String secretName) {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(properties.getBackend().getVault().getHost() + "/secrets/" + secretName))
                    .timeout(properties.getBackend().getVault().getResponseTimeout())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new CredentialResolutionException(
                        "Secret '" + secretName + "' could not be resolved: store answered "
                                + response.statusCode(), null);
            }
            JsonNode body = objectMapper.readTree(response.body());
            JsonNode value = body.get("value");
            if (value == null || value.isNull()) {
                throw new CredentialResolutionException(
                        "Secret '" + secretName + "' resolved to no value", null);
            }
            return value.asText();
        } catch (CredentialResolutionException alreadyTyped) {
            throw alreadyTyped;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CredentialResolutionException("Secret store call interrupted", interrupted);
        } catch (Exception failure) {
            // Attributable to credential resolution in the log, never reported as a backend fault
            // (TOK-001-d). The secret NAME appears; the value never does (ADR-0008, ADR-0015).
            throw new CredentialResolutionException(
                    "Secret store unreachable while resolving '" + secretName + "'", failure);
        }
    }

    /** Test seam: force the next call to go to the store. */
    public void evict() {
        cachedUsername.set(null);
        cachedPassword.set(null);
    }

    private record CachedSecret(String value, Instant expiresAt) {
    }
}
