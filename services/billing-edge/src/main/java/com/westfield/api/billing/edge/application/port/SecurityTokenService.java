package com.westfield.api.billing.edge.application.port;

import com.westfield.api.billing.platform.observability.MigratedFrom;

import java.time.Instant;

/**
 * The WS-Trust exchange at PingFederate: service-account credentials in, SAML assertion out
 * (N-0010, N-0019, N-0073).
 *
 * <p>The wire protocol of the legacy Exchange module is not in the source tree (R-006). Everything
 * observable is the call signature — username, password, audience — and a response consumed as a
 * {@code RequestSecurityTokenResponseCollection} carrying an {@code Assertion} with {@code ID},
 * {@code IssueInstant} and {@code Version} attributes (N-0073 ec 1). The stub in the tests is built
 * to that shape and to nothing more.
 */
@MigratedFrom(value = "km:node/N-0073", note = "module-pingfed:generate-saml; shape per N-0073 ec 1")
public interface SecurityTokenService {

    /**
     * @param audience the environment's audience restriction — this is what stops a non-production
     *                 assertion being accepted by the production billing service (N-0019)
     * @throws com.westfield.api.billing.platform.spi.BackendAssertionProvider.BackendAssertionException
     *         when the STS cannot supply a usable assertion. Never returns null: ADR-0007 forbids
     *         sending an unauthenticated or empty-assertion request to a back end.
     */
    Assertion mint(ServiceAccountCredentials credentials, String audience);

    /**
     * @param wsSecurityHeader the {@code wsse:Security} element, ready to splice into a SOAP envelope
     * @param notOnOrAfter     the assertion's validity horizon. The legacy never reads it
     *                         (N-0019 ec 2); the target only consults it when the cache is enabled
     *                         (ADR-0007), because a cache that outlives the assertion is a new
     *                         failure mode the legacy could not have.
     */
    record Assertion(String wsSecurityHeader, String id, String issueInstant, String version, Instant notOnOrAfter) {
    }
}
