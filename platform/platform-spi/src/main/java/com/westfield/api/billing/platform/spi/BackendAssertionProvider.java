
package com.westfield.api.billing.platform.spi;

/**
 * Supplies the SAML assertion that both back ends require in a WS-Security header (CAP-004).
 * Implemented by {@code billing-edge}; consumed by {@code billing-account} and {@code billing-agency}.
 *
 * <p>ADR-0007 governs the semantics: minted per request by default, cacheable by configuration with a
 * zero default TTL, and a failed or unparseable STS response FAILS the request rather than sending an
 * unauthenticated backend call. An implementation that returns null or an empty assertion on failure
 * violates that decision.
 */
public interface BackendAssertionProvider {

    /**
     * @return the {@code wsse:Security} header element, ready to splice into a SOAP envelope.
     * @throws BackendAssertionException when no valid assertion can be obtained (ADR-0007).
     */
    String assertionHeader();

    /** Raised when the STS cannot supply a usable assertion. Never swallowed (ADR-0007). */
    class BackendAssertionException extends RuntimeException {
        public BackendAssertionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
