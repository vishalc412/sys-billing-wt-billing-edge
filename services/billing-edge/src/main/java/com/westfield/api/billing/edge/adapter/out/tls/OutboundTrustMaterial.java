package com.westfield.api.billing.edge.adapter.out.tls;

import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.platform.observability.MigratedFrom;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;

/**
 * Outbound TLS trust (ADR-0041, CFG-002-e/f/g, TOK-001-h).
 *
 * <p>Loads {@code billing.truststore.*} into a {@link KeyStore} and builds an {@link SSLContext} from
 * it, so both outbound adapters validate server certificates against the CONFIGURED trust material
 * rather than the JVM default (cacerts). The configured location is resolved per environment and the
 * password is the resolved value (ADR-0041: the Thycotic client receives the resolved truststore
 * password), so a rotation takes effect without a redeploy by construction (CFG-002-g): the material
 * is read from the resolved location at context start.
 *
 * <p>No client certificate is presented (CFG-002-i): all trust rests on the assertion in the message,
 * exactly as the legacy does (N-0014), so the {@link KeyManager} array is empty. That holds by
 * construction here, not vacuously.
 *
 * <p>This is the single place that touches {@link KeyStore}/{@link SSLContext}; both adapters call
 * {@link #sslContextFrom(BillingEdgeProperties.Truststore)} so the S5 structural probe can point at
 * one production class that loads the trust material.
 */
@MigratedFrom(value = "km:node/N-0014",
        note = "one trust context for both back ends and the vault; ADR-0041 resolved-password truststore")
public final class OutboundTrustMaterial {

    private OutboundTrustMaterial() {
    }

    /**
     * Builds the {@link SSLContext} the outbound {@code HttpClient}s use. Throws if the configured
     * location does not resolve or cannot be read: startup fails rather than silently widening the
     * trust to the JVM default (CFG-002-e).
     */
    public static SSLContext sslContextFrom(BillingEdgeProperties.Truststore truststore) {
        // A DefaultResourceLoader resolves classpath:, file: and the bare classpath default the same
        // way Spring's @Value/resource injection would, so a unit test that constructs an adapter
        // directly resolves the same material the booted context does.
        ResourceLoader loader = new DefaultResourceLoader();
        Resource resource = loader.getResource(truststore.getLocation());
        if (!resource.exists()) {
            throw new IllegalStateException("billing.truststore.location '" + truststore.getLocation()
                    + "' does not resolve. Startup fails here rather than falling back to the JVM default "
                    + "trust store (ADR-0041, CFG-002-e).");
        }
        try (InputStream in = resource.getInputStream()) {
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            keystore.load(in, truststore.getPassword().toCharArray());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keystore);
            SSLContext context = SSLContext.getInstance("TLS");
            // No KeyManager: the legacy presents no client certificate (N-0014, CFG-002-i).
            context.init(new KeyManager[0], tmf.getTrustManagers(), null);
            return context;
        } catch (Exception failure) {
            throw new IllegalStateException("Could not load the outbound truststore '"
                    + truststore.getLocation() + "' (ADR-0041, CFG-002-e).", failure);
        }
    }
}