package com.westfiled.api.billing.primaryAccount.services;

import com.westfiled.api.billing.configs.interceptors.RequestResponseLoggingFilter;
import com.westfiled.api.billing.primaryAccount.entities.PrimaryAccountRequest;
import com.westfiled.api.billing.primaryAccount.entities.PrimaryAccountResponseXml;
import com.westfiled.api.billing.primaryAccount.entities.PrimaryAccountXml;
import com.westfiled.api.billing.services.RestClientService;
import com.westfiled.api.billing.soap.BillingServiceProperties;
import com.westfiled.api.billing.soap.PingService;
import com.westfiled.api.billing.soap.SamlAssertion;
import com.westfiled.api.billing.soap.SoapUtil;
import com.westfiled.api.billing.soap.XmlUtil;
import com.westfiled.api.billing.soap.exceptions.DownStreamServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Shared downstream call used by the primaryAccount, primaryAccount/transactions and
 * primaryAccount/policy/escrow/transactions endpoints — replaces the callBillingService-primaryAccount
 * sub-flow (primaryAccount-implementation.xml), which is itself flow-ref'd by all three Mule flows.
 *
 * <p>Returns {@link Optional#empty()} both when BillingService responds without a
 * {@code PrimaryAccountResponse} body and when it returns the errorCode=204 "Account not found"
 * SOAP fault — the Mule flows treat both the same way. Callers decide what "not found" means for
 * their own endpoint (204 vs. an empty list — see PrimaryAccountService / TransactionsService).
 */
@Slf4j
@Component
public class PrimaryAccountBillingClient {

    private final BillingServiceProperties billingServiceProperties;
    private final PingService pingService;
    private final SoapUtil soapUtil;
    private final XmlUtil xmlUtil;
    private final RestClientService restClientService;

    public PrimaryAccountBillingClient(BillingServiceProperties billingServiceProperties, PingService pingService,
                                        SoapUtil soapUtil, XmlUtil xmlUtil, RestClientService restClientService) {
        this.billingServiceProperties = billingServiceProperties;
        this.pingService = pingService;
        this.soapUtil = soapUtil;
        this.xmlUtil = xmlUtil;
        this.restClientService = restClientService;
    }

    @CircuitBreaker(name = "billingService", fallbackMethod = "fetchFallback")
    @Retry(name = "billingService", fallbackMethod = "fetchFallback")
    @Observed(name = "billing.primaryaccountbillingclient.fetch", contextualName = "Call BillingService - primaryAccount SOAP endpoint")
    public Optional<PrimaryAccountXml> fetch(String billingAccountNumber, String policyNumber, String policyVersion) {
        SamlAssertion assertion = pingService.fetchAssertion();

        PrimaryAccountRequest request = new PrimaryAccountRequest(billingAccountNumber, policyNumber, policyVersion);
        String correlationId = MDC.get(RequestResponseLoggingFilter.CORRELATION_ID_MDC_KEY);
        String wfesbHeader = soapUtil.buildWfesbHeader(correlationId);
        String envelope = soapUtil.buildEnvelope(assertion, xmlUtil.marshal(request), wfesbHeader);

        log.info("SB-PA-A: Before billing call for billingAccountNumber: {}", billingAccountNumber);
        RestClientService.XmlResponse response = restClientService.postXml(
                billingServiceProperties.getScheme(), billingServiceProperties.getHost(),
                billingServiceProperties.getPort(), billingServiceProperties.getPath(), envelope);
        log.info("SB-PA-B: After billing call for billingAccountNumber: {}", billingAccountNumber);

        if (response.isSuccessful()) {
            return xmlUtil.extractElement(response.getBody(), "PrimaryAccountResponse")
                    .map(fragment -> xmlUtil.unmarshal(fragment, PrimaryAccountResponseXml.class).getPrimaryAccount());
        }

        if (soapUtil.isAccountNotFoundFault(response.getBody())) {
            log.info("SB-PA-C: Account not found for billingAccountNumber: {}", billingAccountNumber);
            return Optional.empty();
        }

        String faultString = soapUtil.extractFaultString(response.getBody()).orElse("BillingService returned HTTP " + response.getStatusCode());
        throw new DownStreamServiceException(faultString);
    }

    @SuppressWarnings("unused")
    public Optional<PrimaryAccountXml> fetchFallback(String billingAccountNumber, String policyNumber, String policyVersion, Throwable ex) {
        log.error("fetchFallback triggered for billingAccountNumber={} due to: {}", billingAccountNumber, ex.getMessage());
        if (ex instanceof DownStreamServiceException downStreamServiceException) {
            throw downStreamServiceException;
        }
        throw new DownStreamServiceException("BillingService call failed", ex);
    }
}
