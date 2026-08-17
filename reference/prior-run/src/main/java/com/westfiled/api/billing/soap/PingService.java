package com.westfiled.api.billing.soap;

import com.westfiled.api.billing.services.RestClientService;
import com.westfiled.api.billing.soap.exceptions.SamlTokenException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Fetches a SAML 2.0 bearer assertion from the PingFederate STS, replacing Mule's GetSAMLToken
 * sub-flow ({@code module-pingfed:generate-saml}). Called fresh for every downstream BillingService
 * request, matching the Mule flows (no token caching/reuse was present in the source).
 *
 * <p>module-pingfed is a closed-source MuleSoft connector, so the exact wire request it sends
 * isn't available; this issues a standard WS-Trust 1.3 RequestSecurityToken with a UsernameToken
 * header, which produces the RSTR/Assertion shape the Mule DataWeave scripts expect. Validate the
 * STS path/request shape against the real PingFederate configuration before go-live — see
 * generated/sys/manual/saml-sts-verification.todo.md.
 */
@Slf4j
@Service
public class PingService {

    private final SamlProperties samlProperties;
    private final TemplateService templateService;
    private final RestClientService restClientService;
    private final XmlUtil xmlUtil;

    public PingService(SamlProperties samlProperties, TemplateService templateService,
                        RestClientService restClientService, XmlUtil xmlUtil) {
        this.samlProperties = samlProperties;
        this.templateService = templateService;
        this.restClientService = restClientService;
        this.xmlUtil = xmlUtil;
    }

    @CircuitBreaker(name = "samlToken", fallbackMethod = "fetchAssertionFallback")
    @Retry(name = "samlToken", fallbackMethod = "fetchAssertionFallback")
    @Observed(name = "billing.pingservice.fetchAssertion", contextualName = "Fetch SAML assertion from PingFederate STS")
    public SamlAssertion fetchAssertion() {
        String requestXml = templateService.render("template/saml_token_request.xml", Map.of(
                "USERNAME", nullToEmpty(samlProperties.getUsername()),
                "PASSWORD", nullToEmpty(samlProperties.getPassword()),
                "WSA_ADDRESS", nullToEmpty(samlProperties.getWsaAddress())));

        RestClientService.XmlResponse response = restClientService.postXml(
                samlProperties.getScheme(), samlProperties.getHost(), samlProperties.getPort(),
                samlProperties.getStsPath(), requestXml);

        if (!response.isSuccessful()) {
            throw new SamlTokenException("PingFederate STS returned HTTP " + response.getStatusCode());
        }

        return xmlUtil.extractElement(response.getBody(), "Assertion")
                .map(SamlAssertion::new)
                .orElseThrow(() -> new SamlTokenException("PingFederate STS response did not contain a SAML Assertion"));
    }

    @SuppressWarnings("unused")
    public SamlAssertion fetchAssertionFallback(Throwable ex) {
        log.error("fetchAssertionFallback triggered due to: {}", ex.getMessage());
        throw new SamlTokenException("Unable to obtain SAML assertion from PingFederate STS", ex);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
