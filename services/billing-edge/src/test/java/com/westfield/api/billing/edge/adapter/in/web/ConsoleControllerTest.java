package com.westfield.api.billing.edge.adapter.in.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westfield.api.billing.edge.adapter.out.audit.RequestScopedAuditRecordAdapter;
import com.westfield.api.billing.edge.application.failure.UnhandledFailurePresenter;
import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.edge.testsupport.Fixtures;
import com.westfield.api.billing.edge.testsupport.RecordingAuditEventSink;
import com.westfield.api.billing.edge.testsupport.TestFilterChain;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CON-001 — the documentation console (N-0031, N-0032, N-0033, ADR-0038).
 *
 * <p>The legacy serves an APIkit console on a HARDCODED {@code /console/*}, <b>unconditionally in
 * production</b>, unaudited: the full contract of a customer-billing API published to anyone who can
 * reach the listener, which binds {@code 0.0.0.0} over plain HTTP (N-0031 defect, N-0013 ec 2).
 *
 * <p>ADR-0038 corrects exactly one thing — production exposure — and preserves everything else,
 * including the two behaviours a tidy-minded implementer would "fix" on sight: the console does not
 * echo the correlation id that every other response carries (N-0032), and console traffic is not
 * audited (N-0031 ec 2). Both are asserted here so that neither can be closed by accident. Auditing
 * console traffic would be an audit-stream change dressed as a security fix, and ADR-0017 rejected it.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("CON-001 documentation console")
class ConsoleControllerTest {

    private static final String CONTRACT = """
            openapi: 3.0.3
            info:
              title: billing-edge
              version: v1
            paths:
              /info: {}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private BillingEdgeProperties properties;

    @BeforeEach
    void setUp() {
        properties = Fixtures.validProperties();
        properties.getConsole().setEnabled(true);
        properties.getConsole().setPath("/console");
        properties.getConsole().setContractLocation("stub:contract");
    }

    private ConsoleController controllerServing(String contract) {
        ResourceLoader loader = new ResourceLoader() {
            @Override
            public Resource getResource(String location) {
                return contract == null
                        ? new ByteArrayResource(new byte[0]) {
                            @Override
                            public boolean exists() {
                                return false;
                            }
                        }
                        : new ByteArrayResource(contract.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public ClassLoader getClassLoader() {
                return getClass().getClassLoader();
            }
        };
        return new ConsoleController(properties, loader);
    }

    @Test // CON-001-a
    @DisplayName("a: where the console is enabled it serves the API's own published contract")
    void servesThePublishedContract() throws Exception {
        ResponseEntity<?> response = controllerServing(CONTRACT).console();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(String.valueOf(response.getBody()))
                .as("what is served is what is published — no transformation, no second copy")
                .contains("openapi: 3.0.3")
                .contains("billing-edge");
    }

    @Test // CON-001-b
    @DisplayName("b: an unrecognised console path answers 404 with the same fixed body the main API uses")
    void unknownConsolePathUsesTheSharedNotFoundBody() throws Exception {
        // N-0033: the console flow handles exactly one error locally, APIKIT:NOT_FOUND, and answers
        // the same {"message": "Resource not found"} the main router does. One shape, two callers.
        ResponseEntity<?> response = controllerServing(null).console();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Map<String, Object> body = objectMapper.convertValue(response.getBody(), new TypeReference<>() {
        });
        assertThat(body).isEqualTo(Map.of("message", "Resource not found"));
    }

    @Test // CON-001-c
    @DisplayName("c: any console failure other than not-found falls through to the default handling")
    void otherConsoleFailuresFallThroughToDefaultHandling() {
        // N-0033 ec: the console error handler catches ONLY not-found. Anything else — an unreadable
        // contract resource, in practice — propagates and is shaped by the service-wide handler.
        // Asserting that the controller does NOT swallow it is the whole point: a local catch here
        // would give console failures a different shape from every other failure in the service.
        ResourceLoader exploding = new ResourceLoader() {
            @Override
            public Resource getResource(String location) {
                return new ByteArrayResource(new byte[0]) {
                    @Override
                    public boolean exists() {
                        return true;
                    }

                    @Override
                    public java.io.InputStream getInputStream() {
                        throw new IllegalStateException("contract resource is unreadable");
                    }
                };
            }

            @Override
            public ClassLoader getClassLoader() {
                return getClass().getClassLoader();
            }
        };

        assertThatThrownBy(() -> new ConsoleController(properties, exploding).console())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unreadable");
    }

    @Test // CON-001-d
    @DisplayName("d: the console answers 200 on success, the failure status on failure, and echoes no correlation id")
    void consoleStatusesAndTheAbsentCorrelationId() throws Exception {
        assertThat(controllerServing(CONTRACT).console().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controllerServing(null).console().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // N-0032: the console flow never sets a status, so it takes the listener default; and it is
        // outside the funnel that adds x-correlation-id, so console responses carry none. Every
        // response from the main API carries one. That difference is preserved, not tidied — a
        // correlation id on a console response would be an id with nothing to correlate to, because
        // no audit record exists for the request.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/console");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TestFilterChain(TestFilterChain.doNothing(), new CorrelationIdFilter(properties))
                .doFilter(request, response);

        assertThat(response.getHeader(RequestFunnel.CORRELATION_ID_HEADER))
                .as("console responses do not echo a correlation id, unlike every main-API response")
                .isNull();
    }

    @Test // CON-001-e
    @DisplayName("e: console traffic is not audited, exactly as today")
    void consoleTrafficIsNotAudited() throws Exception {
        // N-0022 ec 2 / N-0031 ec 2: the legacy listener path '/*' overlaps '/console/*' and Mule
        // resolves to the more specific one, so console requests never enter setRequestResponse and
        // no request/response audit record is written. Reproduced as an explicit predicate rather
        // than left as an accident of matching order.
        RecordingAuditEventSink sink = new RecordingAuditEventSink();
        AuditFunnelFilter funnel = auditFunnel(sink);

        MockHttpServletRequest consoleRequest = new MockHttpServletRequest("GET", "/console/index.html");
        MockHttpServletResponse consoleResponse = new MockHttpServletResponse();
        TestFilterChain consoleChain = new TestFilterChain(TestFilterChain.doNothing(), funnel);
        consoleChain.doFilter(consoleRequest, consoleResponse);

        assertThat(consoleChain.terminalReached()).isTrue();
        assertThat(sink.emissions())
                .as("no audit record of any kind is written for console traffic")
                .isEmpty();

        // The control: an ordinary request through the same funnel IS audited, so the assertion
        // above is about the console rather than about a sink that never records anything.
        MockHttpServletRequest apiRequest = new MockHttpServletRequest("GET", "/info");
        MockHttpServletResponse apiResponse = new MockHttpServletResponse();
        new TestFilterChain(TestFilterChain.doNothing(), auditFunnel(sink)).doFilter(apiRequest, apiResponse);

        assertThat(sink.emissions()).isNotEmpty();
    }

    @Test // CON-001-f
    @DisplayName("f: in production the console is not served and answers the ordinary 404")
    void productionServesNoConsole() throws Exception {
        // ADR-0038's correction. The 404 is the SAME response an unknown console path gets, so a
        // probe cannot distinguish "disabled here" from "no such path" and thereby confirm that a
        // console exists in the other environments.
        properties.getConsole().setEnabled(false);

        ResponseEntity<?> response = controllerServing(CONTRACT).console();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Map<String, Object> body = objectMapper.convertValue(response.getBody(), new TypeReference<>() {
        });
        assertThat(body)
                .as("indistinguishable from an unknown console path, on purpose")
                .isEqualTo(Map.of("message", "Resource not found"));
    }

    @Test // CON-001-g
    @DisplayName("g: the console path and its enablement come from configuration, so one build serves every environment")
    void consolePathAndEnablementAreConfiguration() throws Exception {
        // The legacy hardcodes /console/* in the flow XML, so changing or disabling it is a code
        // change and a redeploy — which is a large part of why it was still on in production.
        properties.getConsole().setPath("/api-docs");

        assertThat(ConsolePaths.isConsole("/api-docs", properties.getConsole().getPath())).isTrue();
        assertThat(ConsolePaths.isConsole("/api-docs/swagger.html", properties.getConsole().getPath())).isTrue();
        assertThat(ConsolePaths.isConsole("/console", properties.getConsole().getPath()))
                .as("the old path stops being the console the moment configuration says so")
                .isFalse();

        // And the same build, with only configuration changed, serves or refuses to serve.
        properties.getConsole().setEnabled(true);
        assertThat(controllerServing(CONTRACT).console().getStatusCode()).isEqualTo(HttpStatus.OK);
        properties.getConsole().setEnabled(false);
        assertThat(controllerServing(CONTRACT).console().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private AuditFunnelFilter auditFunnel(RecordingAuditEventSink sink) {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        return new AuditFunnelFilter(
                properties,
                new RequestScopedAuditRecordAdapter(sink, properties, Clock.systemUTC()),
                new CallerContextFactory(),
                new UnhandledFailurePresenter(),
                new FailureResponseWriter(objectMapper, meterRegistry),
                meterRegistry);
    }
}
