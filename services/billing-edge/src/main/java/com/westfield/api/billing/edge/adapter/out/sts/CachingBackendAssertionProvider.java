package com.westfield.api.billing.edge.adapter.out.sts;

import com.westfield.api.billing.edge.application.port.CredentialProvider;
import com.westfield.api.billing.edge.application.port.SecurityTokenService;
import com.westfield.api.billing.edge.application.port.ServiceAccountCredentials;
import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.platform.observability.MigratedFrom;
import com.westfield.api.billing.platform.spi.BackendAssertionProvider;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Supplies the SAML assertion both back ends require (CAP-004, ADR-0007).
 *
 * <p>Minted PER REQUEST by default. Whether the legacy Exchange module caches cannot be determined
 * from source (N-0010 ec 2, R-006), and that single unknown sets the latency budget: without a cache
 * the API makes two remote calls per request on six of eight endpoints. Rather than guess, the cache
 * ships with a ZERO TTL, so cutover behaviour is a strict per-request port under either legacy
 * behaviour and enabling it later is a configuration change rather than a code change. The
 * {@code billing.saml.mint_count} counter is what will answer the question on day one.
 *
 * <p>When the cache IS enabled it also honours the assertion's {@code NotOnOrAfter}, which the legacy
 * never reads (N-0019 ec 2) — a cache that outlives its assertion is a failure mode the legacy could
 * not have, so it is designed out rather than inherited.
 *
 * <p>An STS failure FAILS the request. It does not send an unauthenticated or empty-assertion call
 * to a billing back end. This is a deliberate divergence (ADR-0007): today the assertion silently
 * disappears and the caller receives the back end's 401, which describes neither the caller's
 * request nor the actual fault.
 */
@Component
@MigratedFrom(value = "km:node/N-0071", note = "GetSAMLToken; ADR-0007 TTL-0 cache and fail-on-STS-failure")
public class CachingBackendAssertionProvider implements BackendAssertionProvider {

    private static final Logger LOG = LoggerFactory.getLogger(CachingBackendAssertionProvider.class);

    private final CredentialProvider credentialProvider;
    private final SecurityTokenService securityTokenService;
    private final BillingEdgeProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final AtomicReference<CachedAssertion> cached = new AtomicReference<>();

    public CachingBackendAssertionProvider(CredentialProvider credentialProvider,
                                           SecurityTokenService securityTokenService,
                                           BillingEdgeProperties properties,
                                           MeterRegistry meterRegistry,
                                           Clock clock) {
        this.credentialProvider = credentialProvider;
        this.securityTokenService = securityTokenService;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @Override
    @MigratedFrom(value = "km:node/N-0071", note = "first processor of every backend-calling flow")
    public String assertionHeader() {
        CachedAssertion current = cached.get();
        if (current != null && current.isUsableAt(clock.instant())) {
            return current.assertion().wsSecurityHeader();
        }
        try (ServiceAccountCredentials credentials = credentialProvider.serviceAccount()) {
            SecurityTokenService.Assertion assertion =
                    securityTokenService.mint(credentials, properties.getSaml().getAudience());
            meterRegistry.counter("billing.saml.mint_count").increment();
            long ttlSeconds = properties.getSaml().getCacheTtlSeconds();
            if (ttlSeconds > 0) {
                cached.set(new CachedAssertion(assertion, clock.instant().plusSeconds(ttlSeconds)));
            }
            return assertion.wsSecurityHeader();
            // The credentials are cleared by the try-with-resources as soon as the exchange has
            // finished. The legacy leaves them in flow variables for the rest of the request
            // (N-0071 ec 3); nothing observable depends on that, and it is not carried forward.
        } catch (BackendAssertionException stsFailure) {
            meterRegistry.counter("mule.parity.sts_failure").increment();
            LOG.error("STS failure — the request fails and NO backend call is made (ADR-0007). "
                    + "This is distinguishable in the log from a billing backend failure.", stsFailure);
            throw stsFailure;
        } catch (CredentialProvider.CredentialResolutionException credentialFailure) {
            meterRegistry.counter("billing.credentials.resolution_failure").increment();
            LOG.error("Credential resolution failure — attributable to the secret store, not to a "
                    + "billing back end (TOK-001-d).", credentialFailure);
            throw new BackendAssertionException("credential resolution failed", credentialFailure);
        }
    }

    /** Test seam and operational escape hatch: drop whatever is cached. */
    public void evict() {
        cached.set(null);
    }

    private record CachedAssertion(SecurityTokenService.Assertion assertion, Instant expiresAt) {
        boolean isUsableAt(Instant now) {
            if (now.isAfter(expiresAt)) {
                return false;
            }
            Instant notOnOrAfter = assertion.notOnOrAfter();
            return notOnOrAfter == null || now.isBefore(notOnOrAfter);
        }
    }
}
