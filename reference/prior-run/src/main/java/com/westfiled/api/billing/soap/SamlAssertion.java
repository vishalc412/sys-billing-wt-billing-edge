package com.westfiled.api.billing.soap;

/**
 * Raw {@code <saml2:Assertion>} XML fragment obtained from the PingFederate STS, ready to be
 * spliced verbatim into the {@code wsse:Security} header of an outbound BillingService request
 * (matching what the Mule flows do with {@code vars.samlResponse...Assertion}).
 */
public record SamlAssertion(String assertionXml) {
}
