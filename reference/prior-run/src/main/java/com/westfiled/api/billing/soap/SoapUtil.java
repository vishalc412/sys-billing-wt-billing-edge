package com.westfiled.api.billing.soap;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Builds the outbound BillingService SOAP envelope (wsse:Security + SAML assertion, optional
 * wfesb header, body) and inspects SOAP fault responses, replacing the DataWeave scripts shared by
 * every Mule flow (callBillingService-primaryAccount, AccountDetailsFlow,
 * get-past-due-today-flow, get-pending-cancellation-today-flow).
 */
@Component
public class SoapUtil {

    private final TemplateService templateService;
    private final XmlUtil xmlUtil;

    public SoapUtil(TemplateService templateService, XmlUtil xmlUtil) {
        this.templateService = templateService;
        this.xmlUtil = xmlUtil;
    }

    public String buildEnvelope(SamlAssertion assertion, String bodyXml) {
        return buildEnvelope(assertion, bodyXml, "");
    }

    public String buildEnvelope(SamlAssertion assertion, String bodyXml, String wfesbHeaderXml) {
        return templateService.render("template/billing_service_payload.xml", Map.of(
                "SAML_ASSERTION", assertion.assertionXml(),
                "WFESB_HEADER", wfesbHeaderXml,
                "BODY", bodyXml));
    }

    /** Only the primaryAccount / transactions / escrow-transactions calls carry this header. */
    public String buildWfesbHeader(String correlationId) {
        return templateService.render("template/wfesb_header.xml", Map.of(
                "CORRELATION_ID", correlationId == null ? "" : correlationId));
    }

    /**
     * True when the SOAP fault is BillingService's "Account not found" (errorCode 204) — every
     * Mule flow reviewed treats this as an empty result (HTTP 204), not an error.
     */
    public boolean isAccountNotFoundFault(String faultXml) {
        Optional<String> errorCode = xmlUtil.extractElementText(faultXml, "errorCode");
        Optional<String> faultString = xmlUtil.extractElementText(faultXml, "faultstring");
        return errorCode.map("204"::equals).orElse(false)
                && faultString.map(s -> s.contains("Account not found")).orElse(false);
    }

    public Optional<String> extractFaultString(String faultXml) {
        return xmlUtil.extractElementText(faultXml, "faultstring");
    }
}
