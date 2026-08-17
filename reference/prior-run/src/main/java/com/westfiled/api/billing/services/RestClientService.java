package com.westfiled.api.billing.services;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Shared outbound HTTP helper wrapping Spring's {@link RestClient}. The downstream BillingService
 * and PingFederate STS are both plain XML-over-HTTP (no JSON), so this only exposes a generic
 * "POST XML, get XML back" call — every SOAP envelope building/parsing lives in
 * {@code soap.SoapUtil} / {@code soap.XmlUtil}.
 *
 * <p>The downstream BillingService returns SOAP faults as non-2xx HTTP responses whose body still
 * needs to be parsed (see the Mule error_Flow, which reads {@code error.muleMessage.typedValue}),
 * so this reads the body itself via {@code exchange} instead of letting {@code retrieve()} throw
 * away the body on error status codes.
 */
@Slf4j
@Service
public class RestClientService {

    private final RestClient restClient;

    public RestClientService(RestClient restClient) {
        this.restClient = restClient;
    }

    public XmlResponse postXml(String scheme, String host, int port, String path, String body) {
        log.debug("POST {}://{}:{}{}", scheme, host, port, path);
        return restClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .scheme(scheme)
                        .host(host)
                        .port(port)
                        .path(path)
                        .build())
                .contentType(MediaType.TEXT_XML)
                .body(body)
                .exchange((request, response) -> new XmlResponse(
                        response.getStatusCode().value(),
                        response.bodyTo(String.class)), false);
    }

    @Value
    public static class XmlResponse {
        int statusCode;
        String body;

        public boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
