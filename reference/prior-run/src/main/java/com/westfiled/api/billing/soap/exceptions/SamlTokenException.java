package com.westfiled.api.billing.soap.exceptions;

/**
 * Raised when the SAML assertion cannot be obtained from the PingFederate STS
 * (module-pingfed:generate-saml equivalent). Every downstream BillingService call depends on a
 * fresh assertion, so this always aborts the request.
 */
public class SamlTokenException extends RuntimeException {

    public SamlTokenException(String message) {
        super(message);
    }

    public SamlTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
