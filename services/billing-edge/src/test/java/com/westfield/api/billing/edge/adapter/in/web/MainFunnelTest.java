package com.westfield.api.billing.edge.adapter.in.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westfield.api.billing.edge.adapter.out.audit.RequestScopedAuditRecordAdapter;
import com.westfield.api.billing.edge.application.failure.UnhandledFailurePresenter;
import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.edge.domain.api.ApiResourceTable;
import com.westfield.api.billing.edge.domain.api.InboundAdmissionRule;
import com.westfield.api.billing.edge.testsupport.Fixtures;
import com.westfield.api.billing.edge.testsupport.ParityBaselines;
import com.westfield.api.billing.edge.testsupport.RecordingAuditEventSink;
import com.westfield.api.billing.edge.testsupport.TestFilterChain;
import com.westfield.api.billing.platform.observability.TracePoint;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADM-001 — inbound listener, response status and the correlation contract (N-0013, N-0023, N-0001).
 * ADM-004 — main request funnel ordering and the audit-trail guarantee (N-0022, N-0030, N-0048).
 * AUD-001-h/i, AUD-004-e/f/g, ERR-003-c/e — the funnel-level obligations those tasks share.
 *
 * <p>The filters are assembled here in the SAME order {@code BillingEdgeConfiguration} assembles
 * them, because that order is the behaviour: the audit funnel sits outside the admission check so a
 * rejected request is still audited, and the admission check sits outside the implementation so a
 * malformed request never reaches one.
 */
@DisplayName("ADM-001 / ADM-004 main request funnel")
class MainFunnelTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-16T10:00:00Z"), ZoneOffset.UTC);

    private BillingEdgeProperties properties;
    private RecordingAuditEventSink sink;
    private RequestScopedAuditRecordAdapter auditRecord;
    private SimpleMeterRegistry meterRegistry;
    private ObjectMapper objectMapper;

    private Filter correlationIdFilter;
    private Filter auditFunnelFilter;
    private Filter inboundValidationFilter;

    @BeforeEach
    void setUp() {
        properties = Fixtures.validProperties();
        sink = new RecordingAuditEventSink();
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        auditRecord = new RequestScopedAuditRecordAdapter(sink, properties, CLOCK);

        UnhandledFailurePresenter presenter = new UnhandledFailurePresenter();
        FailureResponseWriter failureWriter = new FailureResponseWriter(objectMapper, meterRegistry);
        correlationIdFilter = new CorrelationIdFilter(properties);
        auditFunnelFilter = new AuditFunnelFilter(properties, auditRecord, new CallerContextFactory(),
                presenter, failureWriter, meterRegistry);
        inboundValidationFilter = new InboundValidationFilter(
                new InboundAdmissionRule(new ApiResourceTable()),
                new ContractViolationWriter(objectMapper),
                properties);
    }

    @AfterEach
    void tearDown() {
        RequestScopedAuditRecordAdapter.clear();
    }

    private static MockHttpServletRequest get(String uri, String queryString) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        request.addHeader("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.e30.sig");
        request.addHeader("Accept", "application/json");
        if (queryString != null) {
            request.setQueryString(queryString);
            request.setParameter("environment", "TEST");
        }
        return request;
    }

    private TestFilterChain run(MockHttpServletRequest request,
                                MockHttpServletResponse response,
                                TestFilterChain.Terminal terminal) throws Exception {
        TestFilterChain chain = new TestFilterChain(terminal,
                correlationIdFilter, auditFunnelFilter, inboundValidationFilter);
        chain.doFilter(request, response);
        return chain;
    }

    @Test // ADM-001-a
    @DisplayName("a: a path that succeeds without setting a status returns 200")
    void successWithoutAnExplicitStatusIsTwoHundred() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        TestFilterChain chain = run(get("/pastDueToday/A0421", "environment=TEST"), response,
                TestFilterChain.doNothing());

        assertThat(chain.terminalReached()).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test // ADM-001-b
    @DisplayName("b: a path that sets 204 returns 204, not the 200 default")
    void anExplicitTwoOhFourWins() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        run(get("/primaryAccount", null), response, (request, res) -> res.setStatus(204));

        assertThat(response.getStatus()).isEqualTo(204);
    }

    @Test // ADM-001-c
    @DisplayName("c: a failure with no status ever decided returns 500")
    void failureWithNoDecidedStatusIsFiveHundred() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        run(get("/pastDueToday/A0421", "environment=TEST"), response,
                TestFilterChain.failsWith(new IllegalStateException("mapper blew up")));

        assertThat(response.getStatus()).isEqualTo(500);
    }

    @Test // ADM-001-d
    @DisplayName("d: the correlation id is echoed on both success and failure responses")
    void correlationIdIsEchoedOnEveryResponse() throws Exception {
        MockHttpServletResponse success = new MockHttpServletResponse();
        run(get("/pastDueToday/A0421", "environment=TEST"), success, TestFilterChain.doNothing());

        MockHttpServletResponse failure = new MockHttpServletResponse();
        run(get("/pastDueToday/A0421", "environment=TEST"), failure,
                TestFilterChain.failsWith(new IllegalStateException("downstream")));

        // Support has to be able to find a request in the log from what the caller was handed, and a
        // failed request is exactly the one they will ask about.
        assertThat(success.getHeader(RequestFunnel.CORRELATION_ID_HEADER)).isNotBlank();
        assertThat(failure.getHeader(RequestFunnel.CORRELATION_ID_HEADER)).isNotBlank();
    }

    @Test // ADM-001-e
    @DisplayName("e: a path that sets 204 and then fails reports 204 — the stale status is preserved")
    void staleStatusSurvivesADownstreamFailure() throws Exception {
        // The legacy reads ONE variable for the status on both the success and the error response and
        // never resets it between the router and the error handlers (N-0023 ec 1, N-0022 ec 4). A
        // path that decided 204 and then failed therefore answers 204, not a recomputed 500.
        MockHttpServletResponse response = new MockHttpServletResponse();

        run(get("/primaryAccount", null), response, (request, res) -> {
            res.setStatus(204);
            throw new IllegalStateException("failed after the status was decided");
        });

        assertThat(response.getStatus()).isEqualTo(204);

        // PARITY STATUS: this is what we believe the legacy does, not what we have observed it do.
        // R-024 asks whether the effect is observable at all and PAR-002-f is the capture that would
        // answer it. Until then the expectation above is SYNTHETIC and must not be reported as parity.
        assertThat(ParityBaselines.isVerifiedParity("out/golden/ADM-001/stale-status-on-failure"))
                .as("no captured baseline exists for the stale-status effect (R-024, R-025)")
                .isFalse();
    }

    @Test // ADM-001-f
    @DisplayName("f: every JSON-producing path answers application/json")
    void everyJsonProducingPathStatesItsContentType() throws Exception {
        MockHttpServletResponse failure = new MockHttpServletResponse();
        run(get("/pastDueToday/A0421", "environment=TEST"), failure,
                TestFilterChain.failsWith(new IllegalStateException("downstream")));

        MockHttpServletResponse rejected = new MockHttpServletResponse();
        run(get("/nothingLikeThis", null), rejected, TestFilterChain.doNothing());

        assertThat(failure.getContentType()).startsWith("application/json");
        assertThat(rejected.getContentType()).startsWith("application/json");

        // The legacy sets NO Content-Type and lets it be inferred from the DataWeave output directive
        // (N-0023 ec 2). Stating it explicitly changes no observable value and removes an inference —
        // but "changes no observable value" is a claim that needs a census to be evidence, and
        // ADM-001-f's baseline has not been captured (R-025).
        assertThat(ParityBaselines.isVerifiedParity("out/golden/ADM-001/content-type-census")).isFalse();
    }

    @Test // ADM-001-g
    @DisplayName("g: the listener binds all interfaces over plain HTTP, with TLS terminated upstream")
    void listenerBindsAllInterfacesOverPlainHttp() {
        // NFR. The legacy listener is host 0.0.0.0, protocol HTTP, while the RAML advertises HTTPS —
        // the gap is closed by infrastructure, not by the application (N-0013 ec 1 and ec 2). Binding
        // all interfaces is preserved because that is how the platform reaches the pod; what the
        // migration adds is that the service authenticates the caller itself (ADR-0013), so an
        // unauthenticated caller reaching the port directly is no longer served.
        String baseConfiguration = readClasspathResource("application.yaml");

        assertThat(baseConfiguration).contains("port: 8081");
        assertThat(baseConfiguration).doesNotContain("ssl:");
        // No server.address is set anywhere, so the container binds every interface, as today.
        assertThat(baseConfiguration).doesNotContain("address:");
    }

    @Test // ADM-001-h
    @DisplayName("h: no response header comes from an outbound-headers collection mechanism")
    void noOutboundHeaderCollectionMechanismExists() throws Exception {
        // The legacy declares an outbound-headers map and NO path in the application ever writes to
        // it (N-0001 ec 2). Reproducing the mechanism would be reproducing a container for something
        // that is always empty, so it is not reproduced — and this test is what records that the
        // absence is a decision rather than an omission.
        MockHttpServletResponse response = new MockHttpServletResponse();
        run(get("/pastDueToday/A0421", "environment=TEST"), response, TestFilterChain.doNothing());

        assertThat(response.getHeaderNames())
                .as("every header on a success response is one this service set by name")
                .containsExactlyInAnyOrder(RequestFunnel.CORRELATION_ID_HEADER);
    }

    @Test // ADM-004-a
    @DisplayName("a: the audit record is opened before routing and closed after it, on every request")
    void everyRequestIsAuditedAroundRouting() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        TestFilterChain chain = run(get("/pastDueToday/A0421", "environment=TEST"), response,
                TestFilterChain.doNothing());

        assertThat(chain.terminalReached()).isTrue();
        assertThat(sink.at(TracePoint.START)).hasSize(1);
        assertThat(sink.at(TracePoint.END)).hasSize(1);
        // Opened first, closed last: the order of the emissions is the order of the funnel.
        assertThat(sink.emissions().get(0).tracePoint()).isEqualTo(TracePoint.START);
        assertThat(sink.emissions().get(sink.emissions().size() - 1).tracePoint()).isEqualTo(TracePoint.END);
    }

    @Test // ADM-004-b
    @DisplayName("b: a failure after routing begins still emits a request/response audit record")
    void aFailedRequestIsStillAudited() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        run(get("/pastDueToday/A0421", "environment=TEST"), response,
                TestFilterChain.failsWith(new IllegalStateException("downstream")));

        assertThat(sink.at(TracePoint.END)).hasSize(1);
        assertThat(response.getStatus()).isEqualTo(500);
    }

    @Test // ADM-004-c
    @DisplayName("c: the externally visible base path comes from the environment, not from the artifact")
    void basePathComesFromTheEnvironment() {
        // '/*' in every deployed environment and 'sapi-billing/v1/*' only in local, so the externally
        // visible base path is an infrastructure fact (N-0022 ec 1, U12/R-002). Hardcoding it would
        // make the artifact non-portable between environments that differ today.
        properties.getApi().setBasePath("/sapi-billing/v1");
        assertThat(properties.getApi().getBasePath()).isEqualTo("/sapi-billing/v1");

        properties.getApi().setBasePath("/");
        assertThat(properties.getApi().getBasePath()).isEqualTo("/");

        assertThat(readClasspathResource("application-local.yaml")).contains("base-path: /sapi-billing/v1");
        assertThat(readClasspathResource("application-prod.yaml")).contains("base-path: ${BILLING_BASE_PATH:/}");
    }

    @Test // ADM-004-d
    @DisplayName("d: a console request is served by the console handler and is not audited")
    void consoleTrafficIsNotAudited() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/console");
        request.setRequestURI("/console");
        MockHttpServletResponse response = new MockHttpServletResponse();

        TestFilterChain chain = run(request, response, TestFilterChain.doNothing());

        // The legacy console flow never calls setRequestResponse, and its listener path is more
        // specific than the main one, so console traffic bypasses the funnel entirely (N-0031 ec 2,
        // N-0022 ec 2). ADR-0038 gates the console by configuration and deliberately does NOT start
        // auditing it: that would be an audit-stream change smuggled in under a security fix.
        assertThat(chain.terminalReached()).isTrue();
        assertThat(sink.emissions()).isEmpty();
    }

    @Test // ADM-004-e
    @DisplayName("e: the catch-all body is the shared error-handler shape, not the one-field message shape")
    void catchAllBodyIsTheSharedErrorShape() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        run(get("/pastDueToday/A0421", "environment=TEST"), response,
                TestFilterChain.failsWith(new IllegalStateException("downstream")));

        Map<String, Object> body =
                objectMapper.readValue(response.getContentAsString(), new TypeReference<>() {
                });

        // Three distinct error shapes ship (ADR-0042). This is the error_Flow shape; the one-field
        // {"message": ...} belongs to the six contract-violation handlers and to nothing else.
        assertThat(body).containsKeys("faultActor", "errorDesc", "errorType", "errorCause", "correlationId");
        assertThat(body).doesNotContainKey("message");
    }

    @Test // ADM-004-f
    @DisplayName("f: the audit record records the status the caller actually received")
    void auditedStatusMatchesTheStatusReturned() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        run(get("/pastDueToday/A0421", "environment=TEST"), response,
                TestFilterChain.failsWith(new IllegalStateException("downstream")));

        // ADR-0035 CORRECTION. The legacy runs the audit flow BEFORE the shared error handler on the
        // catch-all path, so the one path whose outcome is least understood is the one the audit
        // trail fails to record. Here the status is decided, then recorded, then written.
        Map<String, Object> end = sink.only(TracePoint.END).fields();
        assertThat(end.get("responseStatusCode")).isEqualTo(response.getStatus()).isEqualTo(500);
        // The signal the lost null status accidentally provided is replaced by an explicit marker.
        assertThat(end.get("unhandledFailure")).isEqualTo(true);
    }

    @Test // AUD-001-h
    @DisplayName("h: a documentation console request constructs no audit record at all")
    void consoleConstructsNoAuditRecord() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/console/index.html");
        request.setRequestURI("/console/index.html");

        run(request, new MockHttpServletResponse(), TestFilterChain.doNothing());

        assertThat(sink.emissions()).isEmpty();
        assertThat(RequestScopedAuditRecordAdapter.current()).isNull();
    }

    @Test // AUD-001-i
    @DisplayName("i: a failure building the audit record fails the request before any business logic runs")
    void auditConstructionFailureAbortsTheRequest() throws Exception {
        // The OPPOSITE of the swallow-everything posture used everywhere else in this application,
        // and deliberately so: the legacy makes setRequestResponse a <flow> rather than a <sub-flow>
        // precisely so that it has its own error scope (N-0050 ec 2). A Spring implementer's instinct
        // is to make logging non-fatal; that instinct would silently remove an audit guarantee
        // (ADR-0017).
        sink.failAt(TracePoint.START, new IllegalStateException("audit sink unavailable"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        TestFilterChain chain = run(get("/pastDueToday/A0421", "environment=TEST"), response,
                TestFilterChain.doNothing());

        assertThat(chain.terminalReached()).as("no business logic may run without an audit record").isFalse();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(meterRegistry.counter("billing.audit.construction_failed").count()).isEqualTo(1.0);
        // And the thread is left clean: a leaked record would attach this request's identity to the
        // next request served on the same pooled thread.
        assertThat(RequestScopedAuditRecordAdapter.current()).isNull();
    }

    @Test // AUD-004-e
    @DisplayName("e: on a successful request the recorded status matches the status returned")
    void recordedStatusMatchesOnSuccess() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        run(get("/primaryAccount", null), response, (request, res) -> res.setStatus(204));

        assertThat(sink.only(TracePoint.END).fields().get("responseStatusCode")).isEqualTo(204);
        assertThat(response.getStatus()).isEqualTo(204);
    }

    @Test // AUD-004-f
    @DisplayName("f: a request rejected by a contract-violation handler is audited with that handler's status")
    void contractViolationsAreAuditedWithTheirOwnStatus() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Missing environment parameter on a resource that requires it: a 400 from the admission
        // filter, which sits INSIDE the audit funnel exactly so this record exists.
        TestFilterChain chain = run(get("/pastDueToday/A0421", null), response,
                TestFilterChain.doNothing());

        assertThat(chain.terminalReached()).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(sink.only(TracePoint.END).fields().get("responseStatusCode")).isEqualTo(400);
    }

    @Test // AUD-004-g
    @DisplayName("g: a failure inside audit completion surfaces as an error rather than being absorbed")
    void auditCompletionFailureIsNotAbsorbed() throws Exception {
        sink.failAt(TracePoint.END, new IllegalStateException("audit sink unavailable at exit"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        run(get("/pastDueToday/A0421", "environment=TEST"), response, TestFilterChain.doNothing());

        // N-0048 ec 2: the completion step has its own error scope in the legacy, so its failure is
        // not absorbed by any calling handler. Here it becomes the response.
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(meterRegistry.counter("billing.audit.completion_failed").count()).isEqualTo(1.0);
    }

    @Test // ERR-003-c
    @DisplayName("c: on a non-contract failure the audit record is completed and THEN the error shape is produced")
    void auditIsCompletedBeforeTheErrorPresentationIsWritten() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        run(get("/pastDueToday/A0421", "environment=TEST"), response,
                TestFilterChain.failsWith(new IllegalStateException("downstream")));

        // Ordering per ADR-0035 option B. Asserted through its consequence: the record carries the
        // final status, which is only possible if the status was decided before the record closed.
        assertThat(sink.only(TracePoint.END).fields().get("responseStatusCode")).isEqualTo(500);
        assertThat(response.getContentAsString()).contains("faultActor");
    }

    @Test // ERR-003-e
    @DisplayName("e: audit-construction, routing and shared-logging failures are the paths that reach the catch-all")
    void onlyFunnelLevelFailuresReachTheCatchAll() throws Exception {
        // A failure in audit construction.
        sink.failAt(TracePoint.START, new IllegalStateException("audit construction"));
        MockHttpServletResponse fromAudit = new MockHttpServletResponse();
        run(get("/pastDueToday/A0421", "environment=TEST"), fromAudit, TestFilterChain.doNothing());
        assertThat(fromAudit.getStatus()).isEqualTo(500);
        assertThat(fromAudit.getContentAsString()).contains("faultActor");

        // A failure in routing itself.
        setUp();
        MockHttpServletResponse fromRouting = new MockHttpServletResponse();
        run(get("/pastDueToday/A0421", "environment=TEST"), fromRouting,
                TestFilterChain.failsWith(new IllegalStateException("routing")));
        assertThat(fromRouting.getStatus()).isEqualTo(500);
        assertThat(fromRouting.getContentAsString()).contains("faultActor");

        // A failure in the shared logging dependency, at exit.
        setUp();
        sink.failAt(TracePoint.END, new IllegalStateException("shared logging"));
        MockHttpServletResponse fromLogging = new MockHttpServletResponse();
        run(get("/pastDueToday/A0421", "environment=TEST"), fromLogging, TestFilterChain.doNothing());
        assertThat(fromLogging.getStatus()).isEqualTo(500);
    }

    @Test
    @DisplayName("closing an audit record that was never opened fails rather than inventing one")
    void closingWithoutOpeningFails() {
        // The legacy '++' merge over a null requestResponseLog propagates as a failure rather than
        // producing an empty object (N-0049 ec 2). Substituting an empty record here would invent an
        // audit line describing a request nobody ever described.
        RequestScopedAuditRecordAdapter.clear();

        assertThatThrownBy(() -> auditRecord.close(200, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No audit record was opened");
    }

    private static String readClasspathResource(String name) {
        try (var stream = MainFunnelTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(stream).as("classpath resource %s", name).isNotNull();
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception failure) {
            throw new AssertionError("could not read " + name, failure);
        }
    }

    @Test
    @DisplayName("the funnel emits exactly the two trace points the taxonomy defines for it")
    void tracePointsAreStartAndEnd() throws Exception {
        run(get("/pastDueToday/A0421", "environment=TEST"), new MockHttpServletResponse(),
                TestFilterChain.doNothing());

        assertThat(sink.emissions().stream().map(RecordingAuditEventSink.Emission::tracePoint).toList())
                .isEqualTo(List.of(TracePoint.START, TracePoint.END));
    }
}
