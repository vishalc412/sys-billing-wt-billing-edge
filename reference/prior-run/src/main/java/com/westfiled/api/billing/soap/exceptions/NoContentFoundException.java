package com.westfiled.api.billing.soap.exceptions;

/**
 * Raised when the BillingService response has no matching payload, or returns the
 * errorCode=204 / "Account not found" SOAP fault. Every Mule flow reviewed maps this case to
 * HTTP 204 rather than propagating an error.
 */
public class NoContentFoundException extends RuntimeException {

    public NoContentFoundException(String message) {
        super(message);
    }
}
