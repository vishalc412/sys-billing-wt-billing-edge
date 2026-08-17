package com.westfiled.api.billing.configs;

import com.westfiled.api.billing.soap.TlsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.time.Duration;

/**
 * Single shared {@link RestClient} used for every outbound call (BillingService SOAP, PingFederate
 * STS). Scheme/host/port/path are supplied per-call (see restclient-standard.md), so no base URL
 * is configured here.
 *
 * <p>Mirrors Mule's TLS_Context_Request: when {@code app.tls.truststore-path} points at a mounted
 * truststore (see kustomize's {@code billing-truststore} secret volume), the downstream server
 * certificate is validated against it instead of the JDK default trust store.
 */
@Slf4j
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient(TlsProperties tlsProperties) throws Exception {
        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15));

        if (tlsProperties.getTruststorePath() != null && !tlsProperties.getTruststorePath().isBlank()) {
            httpClientBuilder.sslContext(buildSslContext(tlsProperties));
        } else {
            log.warn("app.tls.truststore-path is not set; using the JDK default trust store for outbound TLS calls");
        }

        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClientBuilder.build()))
                .build();
    }

    private SSLContext buildSslContext(TlsProperties tlsProperties) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("JKS");
        char[] password = tlsProperties.getTruststorePassword() == null
                ? new char[0]
                : tlsProperties.getTruststorePassword().toCharArray();
        try (FileInputStream in = new FileInputStream(tlsProperties.getTruststorePath())) {
            trustStore.load(in, password);
        }
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        return sslContext;
    }
}
