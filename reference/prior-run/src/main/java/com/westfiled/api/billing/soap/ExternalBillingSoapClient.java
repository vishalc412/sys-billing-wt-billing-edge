package com.westfiled.api.billing.soap;

import com.westfiled.api.billing.services.RestClientService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Shared downstream call for the externalPrimaryAccount, pastDueToday and pendingCancelToday
 * flows — all three hit Mule's global HTTP_Request_configuration
 * ({@code externalPrimaryAccount.host}, path {@code /Billing/BillingService}), with a
 * wsse:Security/SAML header and no wfesb header (unlike the primaryAccount.host calls — see
 * {@code primaryAccount.services.PrimaryAccountBillingClient}). Each caller supplies its own SOAP
 * body and interprets faults/response shape itself since the three flows disagree on what "not
 * found" means (see AccountDetailsFlow vs. get-past-due-today-flow / get-pending-cancellation-today-flow).
 */
@Slf4j
@Component
public class ExternalBillingSoapClient {

    private final ExternalPrimaryAccountServiceProperties properties;
    private final SoapUtil soapUtil;
    private final RestClientService restClientService;

    public ExternalBillingSoapClient(ExternalPrimaryAccountServiceProperties properties, SoapUtil soapUtil,
                                      RestClientService restClientService) {
        this.properties = properties;
        this.soapUtil = soapUtil;
        this.restClientService = restClientService;
    }

    @CircuitBreaker(name = "externalBillingService", fallbackMethod = "callFallback")
    @Retry(name = "externalBillingService", fallbackMethod = "callFallback")
    @Observed(name = "billing.externalbillingsoapclient.call", contextualName = "Call BillingService - externalPrimaryAccount SOAP endpoint")
    public RestClientService.XmlResponse call(SamlAssertion assertion, String bodyXml) {
        String envelope = soapUtil.buildEnvelope(assertion, bodyXml);
        return restClientService.postXml(properties.getScheme(), properties.getHost(), properties.getPort(),
                properties.getBillingServicePath(), envelope);
    }

    @SuppressWarnings("unused")
    public RestClientService.XmlResponse callFallback(SamlAssertion assertion, String bodyXml, Throwable ex) {
        log.error("callFallback triggered due to: {}", ex.getMessage());
        throw new com.westfiled.api.billing.soap.exceptions.DownStreamServiceException("BillingService call failed", ex);
    }
}
