
package com.westfield.api.billing.platform.spi;

/**
 * The single implementation of the legacy "account not found" fault test (km §6.6, ADR-0011).
 *
 * <p>The legacy has four copies of this expression and two of them have already drifted in their
 * OUTCOME. Here there is one classification and three different outcomes chosen by the caller:
 * {@code /primaryAccount} answers 204, {@code /primaryAccount/transactions} answers 200 with an empty
 * list, escrow answers 204 (ADR-0027). Do not fold the outcome into the classifier.
 *
 * <p>The test is deliberately exact-case and deliberately assumes an XML fault body. Widening it is
 * a behaviour change (ADR-0011 rejected alternatives B and C).
 */
public interface BillingFaultClassifier {

    enum Classification {
        /** errorCode "204" AND faultstring contains "Account not found", exact case. */
        ACCOUNT_NOT_FOUND,
        /** Any other recognisable SOAP fault. */
        OTHER_FAULT,
        /** Body was not parseable as XML. Increments mule.parity.fault_body_unparseable. */
        UNPARSEABLE
    }

    Classification classify(String faultBody);
}
