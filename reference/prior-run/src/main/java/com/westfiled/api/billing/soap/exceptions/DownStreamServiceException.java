package com.westfiled.api.billing.soap.exceptions;

/**
 * Raised when the downstream BillingService SOAP call fails or returns a SOAP fault other than
 * the "Account not found" case (which maps to {@link NoContentFoundException} instead). Mirrors
 * the Mule error_Flow sub-flow.
 */
public class DownStreamServiceException extends RuntimeException {

    public DownStreamServiceException(String message) {
        super(message);
    }

    public DownStreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
