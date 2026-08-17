package com.westfield.api.billing.edge.adapter.out.sts;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.westfield.api.billing.edge.application.port.CredentialProvider;
import com.westfield.api.billing.edge.application.port.SecurityTokenService;
import com.westfield.api.billing.edge.application.port.ServiceAccountCredentials;
import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.edge.testsupport.Fixtures;
import com.westfield.api.billing.edge.testsupport.MutableClock;
import com.westfield.api.billing.platform.spi.BackendAssertionProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TOK-002 — SAML assertion acquisition and presentation to both back ends (N-0010, N-0019, N-0071,
 * N-0073, ADR-0007).
 *
 * <p>The legacy calls {@code module-pingfed:generate-saml}, an org-internal Exchange module that is
 * not in the source tree. Its wire protocol, timeouts, retries and caching are all unknown (R-006).
 * Everything observable is the call signature — username, password, {@code wsa_Address} — and a
 * response consumed as a {@code RequestSecurityTokenResponseCollection} carrying an
 * {@code Assertion} with {@code ID}, {@code IssueInstant} and {@code Version} (N-0073 ec 1). The stub
 * below is built to exactly that shape and to nothing more, because inventing a richer protocol would
 * make the tests assert a contract nobody has seen.
 *
 * <p>Two deliberate divergences are asserted here, both from ADR-0007:
 * <ul>
 *   <li>the call is BOUNDED by an explicit timeout (i). The legacy exposes none at all, and since an
 *       STS failure now fails the request, an unbounded wait would hold a caller open indefinitely;</li>
 *   <li>an STS failure FAILS the request and makes no backend call (j). Today the assertion silently
 *       resolves to null, the WAS request goes out with the security header dropped and the ESB
 *       request with an empty assertion element — two different failure shapes for one root cause,
 *       both surfacing to the caller as the back end's 401 (N-0019 ec 1).</li>
 * </ul>
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("TOK-002 SAML assertion acquisition")
class SamlAssertionAcquisitionTest {

    /** The WS-Trust response shape N-0073 ec 1 records, and nothing beyond it. */
    private static String rstrc(String assertionId, String notOnOrAfter) {
        String conditions = notOnOrAfter == null ? ""
                : "<saml:Conditions NotOnOrAfter=\"" + notOnOrAfter + "\"/>";
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body>
                    <wst:RequestSecurityTokenResponseCollection\
                     xmlns:wst="http://docs.oasis-open.org/ws-sx/ws-trust/200512">
                      <wst:RequestSecurityTokenResponse>
                        <wst:RequestedSecurityToken>
                          <saml:Assertion xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"\
                           ID="%s" IssueInstant="2026-08-17T09:00:00Z" Version="2.0">%s
                          </saml:Assertion>
                        </wst:RequestedSecurityToken>
                      </wst:RequestSecurityTokenResponse>
                    </wst:RequestSecurityTokenResponseCollection>
                  </soapenv:Body>
                </soapenv:Envelope>""".formatted(assertionId, conditions);
    }

    private WireMockServer sts;
    private BillingEdgeProperties properties;
    private MutableClock clock;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void startSts() {
        sts = new WireMockServer(options().dynamicPort());
        sts.start();
        properties = Fixtures.validProperties();
        properties.getBackend().getSts().setHost("http://localhost:" + sts.port());
        properties.getSaml().setAudience("https://test.westfieldgrp.com/billing");
        properties.getSaml().setCacheTtlSeconds(0);
        clock = new MutableClock(Instant.parse("2026-08-17T09:00:00Z"));
        meterRegistry = new SimpleMeterRegistry();
    }

    @AfterEach
    void stopSts() {
        sts.stop();
    }

    private void stubAssertion(String id, String notOnOrAfter) {
        sts.stubFor(any(anyUrl()).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml")
                .withBody(rstrc(id, notOnOrAfter))));
    }

    /** A credential provider that hands out fixed credentials and records how often it was asked. */
    private static final class CountingCredentials implements CredentialProvider {
        private int calls;

        @Override
        public ServiceAccountCredentials serviceAccount() {
            calls++;
            return new ServiceAccountCredentials("svc_billing", "s3cr3t".toCharArray());
        }
    }

    private CachingBackendAssertionProvider providerWith(CredentialProvider credentials) {
        return new CachingBackendAssertionProvider(
                credentials,
                new WsTrustSecurityTokenService(properties),
                properties,
                meterRegistry,
                clock);
    }

    @Test // TOK-002-a
    @DisplayName("a: the assertion is obtained before the backend request is built and is the first external call")
    void theAssertionIsObtainedFirst() {
        // N-0071: GetSAMLToken is the FIRST processor of every backend-calling flow. Nothing about a
        // billing request is sent anywhere before the assertion exists — which is also what makes
        // ADR-0007's fail-on-STS-failure safe: there is no partially-issued backend call to unwind.
        stubAssertion("assertion-1", null);
        CountingCredentials credentials = new CountingCredentials();

        String header = providerWith(credentials).assertionHeader();

        assertThat(header).isNotBlank();
        assertThat(credentials.calls)
                .as("the credentials are fetched, then the assertion minted, before anything else")
                .isEqualTo(1);
        assertThat(sts.getAllServeEvents())
                .as("the STS exchange is the first and only external interaction so far")
                .hasSize(1);
    }

    @Test // TOK-002-b
    @DisplayName("b: a non-production deployment scopes its assertion to the non-production audience")
    void audienceIsScopedPerEnvironment() {
        // N-0019: the audience restriction is the ONLY thing that stops a non-production assertion
        // being accepted by the production billing service. Not a config tidy — a boundary.
        stubAssertion("assertion-1", null);
        properties.getSaml().setAudience("https://test.westfieldgrp.com/billing");

        providerWith(new CountingCredentials()).assertionHeader();

        String sentToSts = sts.getAllServeEvents().get(0).getRequest().getBodyAsString();
        assertThat(sentToSts)
                .as("the request carries the deployment's own audience")
                .contains("https://test.westfieldgrp.com/billing");
        assertThat(sentToSts)
                .as("and never production's")
                .doesNotContain("https://www.westfieldgrp.com/billing");

        sts.resetRequests();
        properties.getSaml().setAudience("https://www.westfieldgrp.com/billing");
        providerWith(new CountingCredentials()).assertionHeader();

        assertThat(sts.getAllServeEvents().get(0).getRequest().getBodyAsString())
                .contains("https://www.westfieldgrp.com/billing");
    }

    @Test // TOK-002-c
    @DisplayName("c: the assertion is wrapped in a mustUnderstand wsse:Security header carrying ID, IssueInstant and Version")
    void theAssertionIsPresentedInAMustUnderstandSecurityHeader() {
        stubAssertion("_a1b2c3", null);

        SecurityTokenService.Assertion assertion = new WsTrustSecurityTokenService(properties)
                .mint(new ServiceAccountCredentials("svc_billing", "s3cr3t".toCharArray()),
                        properties.getSaml().getAudience());

        // The three attributes N-0073 ec 1 records as carried from the token response.
        assertThat(assertion.id()).isEqualTo("_a1b2c3");
        assertThat(assertion.issueInstant()).isEqualTo("2026-08-17T09:00:00Z");
        assertThat(assertion.version()).isEqualTo("2.0");

        // mustUnderstand="1" is what stops the back end silently ignoring the header — which is
        // exactly the failure the legacy produces when the assertion resolves to null (N-0019 ec 1).
        assertThat(assertion.wsSecurityHeader())
                .contains("wsse:Security")
                .contains("mustUnderstand=\"1\"")
                .contains("Assertion")
                .contains("_a1b2c3");
    }

    @Test // TOK-002-f
    @DisplayName("f: validity is checked only where a cache could outlive the assertion, per the accepted decision")
    void assertionValidityIsCheckedOnlyWhereACacheCouldOutliveIt() {
        // ADR-0007 required this to be stated explicitly rather than left implied. The legacy NEVER
        // reads NotOnOrAfter (N-0019 ec 2), and with the default zero TTL neither does the target:
        // a freshly minted assertion is used immediately, exactly as today. The check exists only for
        // the case the legacy could not have — a CACHED assertion outliving its own validity.
        properties.getSaml().setCacheTtlSeconds(3600);
        stubAssertion("assertion-1", "2026-08-17T09:05:00Z");

        CountingCredentials credentials = new CountingCredentials();
        CachingBackendAssertionProvider provider = providerWith(credentials);

        provider.assertionHeader();
        assertThat(credentials.calls).isEqualTo(1);

        // Inside the TTL and inside validity: the cached assertion is reused.
        clock.advance(Duration.ofMinutes(1));
        provider.assertionHeader();
        assertThat(credentials.calls).isEqualTo(1);

        // Still inside the one-hour TTL, but the assertion has now expired. A cache that handed this
        // out would produce a backend 401 that nothing in the logs would explain.
        clock.advance(Duration.ofMinutes(10));
        stubAssertion("assertion-2", "2026-08-17T10:30:00Z");
        provider.assertionHeader();

        assertThat(credentials.calls)
                .as("an expired cached assertion is re-minted rather than presented")
                .isEqualTo(2);
    }

    @Test // TOK-002-g
    @DisplayName("g: with the cache TTL at zero, two requests perform two token exchanges")
    void zeroTtlMintsPerRequest() {
        // The cutover default. Whether the legacy Exchange module caches cannot be determined from
        // source (R-006), and that one unknown sets the whole latency budget: without a cache the API
        // makes two remote calls per request on six of eight endpoints. Rather than guess, TTL 0
        // reproduces a strict per-request port under EITHER legacy behaviour, and the mint counter is
        // what will answer the question on day one.
        properties.getSaml().setCacheTtlSeconds(0);
        stubAssertion("assertion-1", null);

        CountingCredentials credentials = new CountingCredentials();
        CachingBackendAssertionProvider provider = providerWith(credentials);

        provider.assertionHeader();
        provider.assertionHeader();

        assertThat(sts.getAllServeEvents()).hasSize(2);
        assertThat(credentials.calls).isEqualTo(2);
        assertThat(meterRegistry.counter("billing.saml.mint_count").count())
                .as("the counter that turns R-006 from a guess into a measurement")
                .isEqualTo(2);
    }

    @Test // TOK-002-h
    @DisplayName("h: with a non-zero TTL, two requests inside the window perform one token exchange")
    void nonZeroTtlMintsOnce() {
        properties.getSaml().setCacheTtlSeconds(300);
        stubAssertion("assertion-1", null);

        CountingCredentials credentials = new CountingCredentials();
        CachingBackendAssertionProvider provider = providerWith(credentials);

        provider.assertionHeader();
        clock.advance(Duration.ofSeconds(30));
        provider.assertionHeader();

        assertThat(sts.getAllServeEvents()).hasSize(1);
        assertThat(credentials.calls).isEqualTo(1);

        // And past the window it mints again, so the TTL is a bound rather than a one-time fetch.
        clock.advance(Duration.ofSeconds(600));
        provider.assertionHeader();
        assertThat(sts.getAllServeEvents()).hasSize(2);
    }

    @Test // TOK-002-i
    @DisplayName("i: a slow token service is bounded by an explicit configured timeout, not an unbounded wait")
    void aSlowTokenServiceIsBounded() {
        // A deliberate improvement: the legacy has no bound at all (N-0010 ec 1, N-0073 ec 2), so a
        // hanging STS is bounded only by whatever an opaque module defaults to. Because ADR-0007 now
        // makes an STS failure fail the request, an unbounded wait would hold the caller's connection
        // open for as long as PingFederate is unwell.
        properties.getBackend().getSts().setResponseTimeout(Duration.ofMillis(300));
        sts.stubFor(any(anyUrl()).willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(5_000)
                .withBody(rstrc("assertion-1", null))));

        long startedAt = System.nanoTime();
        assertThatThrownBy(() -> providerWith(new CountingCredentials()).assertionHeader())
                .isInstanceOf(BackendAssertionProvider.BackendAssertionException.class);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(elapsed)
                .as("the call must abandon at roughly the configured bound, not at the stub's delay")
                .isLessThan(Duration.ofSeconds(4));
        assertThat(properties.getBackend().getSts().getResponseTimeout())
                .as("the bound is an explicit configured number, not a connector default")
                .isNotNull();
    }

    @Test // TOK-002-j
    @DisplayName("j: an STS failure is distinguishable in the log from a billing backend failure")
    void stsFailureIsDistinguishableFromABackendFailure() {
        // The diagnostic point of ADR-0007. Today the assertion silently disappears and the caller
        // gets the BACK END's 401, so an STS outage looks exactly like a billing authorisation
        // problem and the first hour of the incident is spent in the wrong system.
        sts.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(500)));

        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            assertThatThrownBy(() -> providerWith(new CountingCredentials()).assertionHeader())
                    .isInstanceOf(BackendAssertionProvider.BackendAssertionException.class)
                    .hasMessageContaining("STS answered 500");

            assertThat(appender.list)
                    .as("the log must name the STS as the origin, and say no backend call was made")
                    .anyMatch(event -> event.getFormattedMessage().contains("STS failure")
                            && event.getFormattedMessage().contains("NO backend call"));

            assertThat(meterRegistry.counter("mule.parity.sts_failure").count()).isEqualTo(1);
        } finally {
            detachAppender(appender);
        }

        // A credential-resolution failure is a THIRD distinct thing and must not be reported as
        // either of the other two (TOK-001-d).
        CredentialProvider broken = () -> {
            throw new CredentialProvider.CredentialResolutionException("vault unreachable", null);
        };
        assertThatThrownBy(() -> providerWith(broken).assertionHeader())
                .isInstanceOf(BackendAssertionProvider.BackendAssertionException.class)
                .hasMessageContaining("credential resolution failed");
        assertThat(meterRegistry.counter("billing.credentials.resolution_failure").count()).isEqualTo(1);
    }

    // ---------------------------------------------------------------------------------------------

    private static ListAppender<ILoggingEvent> attachAppender() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(context);
        appender.start();
        ch.qos.logback.classic.Logger root = context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.DEBUG);
        root.addAppender(appender);
        return appender;
    }

    private static void detachAppender(ListAppender<ILoggingEvent> appender) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME).detachAppender(appender);
        appender.stop();
    }
}
