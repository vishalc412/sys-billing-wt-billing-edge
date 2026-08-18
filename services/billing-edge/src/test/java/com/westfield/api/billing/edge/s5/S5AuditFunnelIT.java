package com.westfield.api.billing.edge.s5;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The audit stream, observed as it is actually emitted by a running container.
 *
 * <p>The S4 suite asserts the audit projections by calling the record builders directly. That proves
 * the projection is correct; it proves nothing about whether the funnel is wired so that the record is
 * emitted at all, on which paths, and carrying which status. This class reads the emitted log stream.
 */
@SpringBootTest(
        classes = {com.westfield.api.billing.edge.SysBillingApplication.class, S5TestSupport.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("S5 — the audit funnel of a booted container")
class S5AuditFunnelIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private ListAppender<ILoggingEvent> auditLog;

    @BeforeEach
    void captureTheAuditStream() {
        Logger logger = (Logger) LoggerFactory.getLogger("com.westfield.api.billing.audit");
        logger.setLevel(Level.INFO);
        auditLog = new ListAppender<>();
        auditLog.start();
        logger.detachAppender("s5");
        auditLog.setName("s5");
        logger.addAppender(auditLog);
    }

    private ResponseEntity<String> get(String path, HttpHeaders headers) {
        return rest.exchange("http://localhost:" + port + path, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> records() {
        List<Map<String, Object>> parsed = new ArrayList<>();
        for (ILoggingEvent event : auditLog.list) {
            try {
                parsed.add(MAPPER.readValue(event.getFormattedMessage(), Map.class));
            } catch (Exception notJson) {
                throw new AssertionError("AUD-006-a requires every audit entry to be structured JSON; "
                        + "this one was not: " + event.getFormattedMessage(), notJson);
            }
        }
        return parsed;
    }

    private List<Map<String, Object>> withTracePoint(String tracePoint) {
        return records().stream().filter(r -> tracePoint.equals(r.get("tracePoint"))).toList();
    }

    // -------------------------------------------------------------------------------------------

    @Test // ADM-004-a, AUD-001-a, AUD-006-a, AUD-006-b
    @DisplayName("ADM-004-a/AUD-006-a: a successful request emits a START and an END record, both structured JSON with the correlation id")
    void aSuccessfulRequestIsAuditedAtBothEnds() {
        get("/info", bearer(S5TestSupport.VALID));

        List<Map<String, Object>> start = withTracePoint("START");
        List<Map<String, Object>> end = withTracePoint("END");

        assertThat(start).as("the entry record").isNotEmpty();
        assertThat(end).as("the completion record").hasSize(1);
        assertThat(records()).allSatisfy(record ->
                assertThat(record.get("correlationId")).as("every entry carries the correlation id").isNotNull());
        // AUD-001-a: the fixed marker, the api name, version and layer.
        Map<String, Object> requestResponse = start.stream()
                .filter(r -> "UWSAPI-REQRES".equals(r.get("identifier"))).findFirst().orElseThrow();
        assertThat(requestResponse).containsEntry("apiName", "sapi-billing")
                .containsEntry("apiVersion", "v1").containsEntry("apiLayer", "SAPI");
        assertThat(requestResponse).containsKeys("entryTimestamp", "entryTimeMillis", "requestUri",
                "requestTemplate", "sub", "email", "clientId", "agencyCodes", "impersonated",
                "actSub", "actEmail");
    }

    @Test // AUD-004-e, ADM-004-f
    @DisplayName("AUD-004-e: the recorded status matches the status actually returned on a success")
    void theRecordedStatusMatchesTheReturnedStatusOnSuccess() {
        ResponseEntity<String> response = get("/info", bearer(S5TestSupport.VALID));

        Map<String, Object> end = withTracePoint("END").get(0);
        assertThat(end.get("responseStatusCode")).isEqualTo(response.getStatusCode().value());
        assertThat(end).containsEntry("unhandledFailure", false);
        assertThat(end).containsKey("elapsedTime");
    }

    @Test // AUD-004-f, ADM-004-a
    @DisplayName("AUD-004-f: a contract-violation rejection is still audited, with the status the handler returned")
    void aContractViolationIsAuditedWithItsOwnStatus() {
        ResponseEntity<String> response = get("/nope", bearer(S5TestSupport.VALID));
        assertThat(response.getStatusCode().value()).isEqualTo(404);

        Map<String, Object> end = withTracePoint("END").get(0);
        assertThat(end.get("responseStatusCode"))
                .as("the audit record must carry the 404 the caller received, not a default")
                .isEqualTo(404);
    }

    @Test // AUD-004-f — the missing-Authorization rejection
    @DisplayName("AUD-004-f: a request rejected for a missing Authorization header is audited with 400")
    void aMissingAuthorizationRejectionIsAudited() {
        get("/info", new HttpHeaders());

        Map<String, Object> end = withTracePoint("END").get(0);
        assertThat(end).containsEntry("responseStatusCode", 400);
    }

    @Test // AUD-001-e, SEC-001-d
    @DisplayName("AUD-001-e: with no decoded token, clientId is null while its neighbours are EMPTY")
    void withNoTokenClientIdIsNullAndNeighboursAreEmpty() {
        get("/info", new HttpHeaders());

        Map<String, Object> record = withTracePoint("START").stream()
                .filter(r -> "UWSAPI-REQRES".equals(r.get("identifier"))).findFirst().orElseThrow();
        assertThat(record).containsEntry("clientId", null);
        assertThat(record).containsEntry("sub", "EMPTY").containsEntry("email", "EMPTY")
                .containsEntry("actSub", "EMPTY").containsEntry("actEmail", "EMPTY");
        assertThat(record.get("agencyCodes")).isEqualTo(List.of());
    }

    @Test // AUD-001-b
    @DisplayName("AUD-001-b: the interaction correlation id and the service's own id are separate fields")
    void bothCorrelationIdentifiersAreRecordedSeparately() {
        HttpHeaders headers = bearer(S5TestSupport.VALID);
        headers.set("x-interaction-correlation-id", "interaction-9876");
        headers.set("x-correlation-id", "service-1234");

        get("/info", headers);

        Map<String, Object> record = withTracePoint("START").stream()
                .filter(r -> "UWSAPI-REQRES".equals(r.get("identifier"))).findFirst().orElseThrow();
        assertThat(record).containsEntry("correlationId", "service-1234")
                .containsEntry("interactionCorrelationId", "interaction-9876");
    }

    @Test // AUD-002-b, AUD-002-h
    @DisplayName("AUD-002-b/h: the account number is masked in requestTemplate and retained in requestUri")
    void theAccountNumberIsMaskedInTheTemplateOnly() {
        get("/externalPrimaryAccount/1234567890/account-billing?environment=TEST",
                bearer(S5TestSupport.VALID));

        Map<String, Object> record = withTracePoint("START").stream()
                .filter(r -> "UWSAPI-REQRES".equals(r.get("identifier"))).findFirst().orElseThrow();
        assertThat((String) record.get("requestTemplate"))
                .isEqualTo("/externalPrimaryAccount/X/account-billing");
        assertThat((String) record.get("requestUri"))
                .as("the raw URI keeps the identifier (N-0051 rule 3)")
                .contains("1234567890");
    }

    @Test // AUD-005-a, AUD-005-h
    @DisplayName("AUD-005-a/h: /info emits a per-endpoint record carrying the calling system and the API key header")
    void infoEmitsThePerEndpointRecord() {
        HttpHeaders headers = bearer(S5TestSupport.VALID);
        headers.set("x-calling-system", "exp-billing");
        headers.set("x-api-key", "an-api-key");

        get("/info", headers);

        Map<String, Object> endpointRecord = withTracePoint("START").stream()
                .filter(r -> r.containsKey("uriTemplate")).findFirst()
                .orElseThrow(() -> new AssertionError("no per-endpoint audit record was emitted for /info"));
        assertThat(endpointRecord).containsEntry("callingSystem", "exp-billing")
                .containsEntry("apiKey", "an-api-key")
                .containsEntry("uriTemplate", "/info")
                .containsEntry("httpMethod", "GET");
    }

    @Test // AUD-005-g
    @DisplayName("AUD-005-g: /primaryAccount emits NO per-endpoint record while still emitting a request/response record")
    void primaryAccountEmitsNoPerEndpointRecord() {
        get("/primaryAccount", bearer(S5TestSupport.VALID));

        assertThat(withTracePoint("START"))
                .as("no per-endpoint record: the three /primaryAccount resources are absent from that stream")
                .noneMatch(r -> r.containsKey("uriTemplate"));
        assertThat(withTracePoint("START"))
                .as("but the request/response record is still emitted")
                .anyMatch(r -> "UWSAPI-REQRES".equals(r.get("identifier")));
        assertThat(withTracePoint("END")).hasSize(1);
    }

    @Test // ADM-004-d, AUD-001-h, CON-001-e
    @DisplayName("ADM-004-d/CON-001-e: a console request emits NO audit record at all")
    void consoleTrafficIsUnaudited() {
        get("/console", new HttpHeaders());

        assertThat(records()).as("console traffic is unaudited, as today").isEmpty();
    }

    @Test // AUD-003-h — the deliberate disagreement, observed end to end
    @DisplayName("AUD-003-h: for a flat-actSub token the two audit streams DISAGREE on impersonation (ADR-0036 preserves this)")
    void theTwoImpersonationDefinitionsDisagreeOnTheWire() {
        String token = S5TestSupport.tokenWith("s5-flat-actor",
                Map.of("sub", "u1", "email", "u1@westfieldgrp.com", "clientId", "client-a",
                        "actSub", "supervisor-7", "actEmail", "sup@westfieldgrp.com",
                        "agencyCodes", List.of("A0421")));

        get("/info", bearer(token));

        Map<String, Object> requestResponse = withTracePoint("START").stream()
                .filter(r -> "UWSAPI-REQRES".equals(r.get("identifier"))).findFirst().orElseThrow();
        Map<String, Object> perEndpoint = withTracePoint("START").stream()
                .filter(r -> r.containsKey("uriTemplate")).findFirst().orElseThrow();

        assertThat(requestResponse).containsEntry("impersonated", true);
        assertThat(perEndpoint)
                .as("AUD-003-h as written asserts that both streams record the SAME outcome. "
                        + "ADR-0036 was accepted as PRESERVE, so they deliberately differ. The "
                        + "criterion and the accepted ADR contradict each other — DEF-0107.")
                .containsEntry("impersonated", false);
    }

    @Test // AUD-006-c — masking on the emitted entry, observed
    @DisplayName("AUD-006-c: the raw request URI is NOT masked in the emitted audit entry")
    void theRawRequestUriIsNotMaskedOnTheWayOut() {
        get("/externalPrimaryAccount/1234567890/account-billing?environment=TEST",
                bearer(S5TestSupport.VALID));

        // billing.logging.mask-fields ships with billingAccountNumber/policyNumber/agencyCode, but
        // StructuredAuditEventSink matches on the FIELD NAME of the emitted map. The account number
        // travels inside the value of `requestUri`, which is not a masked field name, so it reaches
        // the log in full. That is N-0051 rule 3 behaviour and it is intended; it is asserted here so
        // that the limit of the masking rule is on the record.
        Map<String, Object> record = withTracePoint("START").stream()
                .filter(r -> "UWSAPI-REQRES".equals(r.get("identifier"))).findFirst().orElseThrow();
        assertThat((String) record.get("requestUri")).contains("1234567890");
    }
}
