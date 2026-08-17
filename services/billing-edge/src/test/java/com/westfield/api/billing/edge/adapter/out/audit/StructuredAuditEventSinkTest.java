package com.westfield.api.billing.edge.adapter.out.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.edge.testsupport.Fixtures;
import com.westfield.api.billing.platform.observability.TracePoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * AUD-006 — the structured logging taxonomy and the masking that ADR-0015 adds (N-0009, N-0015).
 *
 * <p>Two things are being preserved and one is being changed, and it is worth keeping them apart.
 *
 * <p><b>Preserved:</b> the trace-point taxonomy — start, before-request, after-request, exception,
 * end — and the correlation id on every entry. Downstream analytics groups on those values, so they
 * are reproduced exactly rather than modernised into whatever a Spring service would naturally emit.
 *
 * <p><b>Changed, deliberately:</b> the legacy defines {@code json.data.mask.fields} and
 * {@code json.data.disable.fields} and leaves them <b>empty in all six environments including
 * production</b> (N-0009 ec 1, N-0101 ec 6). Nothing is masked. What that writes to the production
 * log is the full SOAP request <b>including the SAML assertion</b>, at seven sites, on every
 * backend-calling flow, plus full fault payloads and the account identifiers inside those envelopes.
 * ADR-0015 ships non-empty defaults. It is a rare security fix that costs nothing on the wire,
 * because it changes the log only — no response body is altered, including {@code error.cause}, which
 * remains a known and knowingly-accepted exposure belonging to ADR-0042.
 *
 * <p>One tension is resolved explicitly here (f): serialising an awkward payload must never fail a
 * request, even though a failure CONSTRUCTING the audit record must (ADR-0017). Emitting a log line
 * about a payload and guaranteeing an audit trail are different obligations and are kept apart.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("AUD-006 structured logging taxonomy and masking")
class StructuredAuditEventSinkTest {

    private BillingEdgeProperties properties;
    private StructuredAuditEventSink sink;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        properties = Fixtures.validProperties();
        sink = new StructuredAuditEventSink(new ObjectMapper(), properties);
        appender = attachAppender();
    }

    @AfterEach
    void tearDown() {
        detachAppender(appender);
    }

    private List<String> emittedLines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test // AUD-006-a
    @DisplayName("a: every entry is structured JSON carrying the correlation id and a trace point from the fixed set")
    void everyEntryIsJsonWithACorrelationIdAndATracePoint() {
        for (TracePoint tracePoint : TracePoint.values()) {
            sink.emit(tracePoint, "corr-1", Map.of("event", tracePoint.name().toLowerCase()));
        }

        assertThat(TracePoint.values())
                .as("the taxonomy is the legacy's, exactly — analytics groups on these values")
                .extracting(Enum::name)
                .containsExactly("START", "BEFORE_REQUEST", "AFTER_REQUEST", "EXCEPTION", "END");

        List<String> lines = emittedLines();
        assertThat(lines).hasSize(TracePoint.values().length);
        for (String line : lines) {
            assertThat(line).startsWith("{").endsWith("}");
            assertThat(line).contains("\"correlationId\":\"corr-1\"");
            assertThat(line).contains("\"tracePoint\":");
        }
    }

    @Test // AUD-006-b
    @DisplayName("b: a request can be reconstructed end to end from the log stream alone")
    void aRequestIsReconstructableFromTheLogStream() {
        // The reason the taxonomy exists. One correlation id, five ordered trace points, and a
        // reader who was not there can say what happened without access to anything else.
        sink.emit(TracePoint.START, "corr-7", Map.of("requestUri", "/pastDueToday/A0421"));
        sink.emit(TracePoint.BEFORE_REQUEST, "corr-7", Map.of("backend", "esb"));
        sink.emit(TracePoint.AFTER_REQUEST, "corr-7", Map.of("backendStatus", 200));
        sink.emit(TracePoint.END, "corr-7", Map.of("responseStatusCode", 200, "elapsedTime", 42));

        List<String> lines = emittedLines();
        assertThat(lines).hasSize(4);
        assertThat(lines).allMatch(line -> line.contains("corr-7"));
        assertThat(lines.get(0)).contains("START").contains("/pastDueToday/A0421");
        assertThat(lines.get(1)).contains("BEFORE_REQUEST");
        assertThat(lines.get(2)).contains("AFTER_REQUEST").contains("200");
        assertThat(lines.get(3)).contains("END").contains("elapsedTime");
    }

    @Test // AUD-006-c
    @DisplayName("c: a configured masked field has its value redacted in the emitted entry")
    void configuredFieldsAreMasked() {
        // The assertion is replaced outright; identifiers go through the SAME masking rule the audit
        // trail uses, so one rule governs both streams (ADR-0015). A second, subtly different
        // redaction rule is exactly what that decision set out to prevent.
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("assertion", "<saml:Assertion>...credential material...</saml:Assertion>");
        fields.put("billingAccountNumber", "1234567890");
        fields.put("requestUri", "/externalPrimaryAccount/1234567890/account-billing");

        Map<String, Object> redacted = sink.redact(fields);

        assertThat(redacted.get("assertion")).isEqualTo(StructuredAuditEventSink.REDACTED);
        assertThat(redacted.get("billingAccountNumber"))
                .as("an identifier is masked by the audit trail's own rule, not replaced wholesale")
                .isEqualTo("X");
        assertThat(redacted.get("requestUri"))
                .as("a field that is not on the list is untouched")
                .isEqualTo("/externalPrimaryAccount/1234567890/account-billing");

        // Nested payloads are the case that matters: the assertion lives inside an envelope.
        Map<String, Object> nested = Map.of("soapRequest",
                new LinkedHashMap<>(Map.of("assertion", "<saml:Assertion/>")));
        Map<String, Object> redactedNested = sink.redact(nested);
        assertThat(String.valueOf(redactedNested.get("soapRequest")))
                .contains(StructuredAuditEventSink.REDACTED)
                .doesNotContain("saml:Assertion");
    }

    @Test // AUD-006-d
    @DisplayName("d: a configured suppressed field is absent from the emitted entry, not merely empty")
    void configuredFieldsAreSuppressed() {
        // "disable" meant absent, not blank. A field emitted as "" still tells a log reader that the
        // key existed and something was there, which is not what suppression is for.
        properties.getLogging().setDisableFields(List.of("soapResponse"));

        Map<String, Object> redacted = sink.redact(new LinkedHashMap<>(Map.of(
                "soapResponse", "<envelope>...</envelope>",
                "correlationId", "corr-1")));

        assertThat(redacted).doesNotContainKey("soapResponse");
        assertThat(redacted).containsEntry("correlationId", "corr-1");
    }

    @Test // AUD-006-e
    @DisplayName("e: a SOAP log entry has its assertion and account identifiers redacted in every environment")
    void soapEntriesAreRedactedInEveryEnvironment() {
        // ADR-0015 option B, and the criterion that makes the divergence concrete. The legacy masks
        // NOTHING, anywhere, so this exact entry currently writes a usable SAML assertion into the
        // production log. The defaults ship non-empty, so the redaction holds without any
        // environment having to remember to configure it.
        assertThat(new BillingEdgeProperties().getLogging().getMaskFields())
                .as("the defaults must be non-empty, unlike all six legacy environments")
                .isNotEmpty()
                .contains("assertion", "billingAccountNumber", "policyNumber", "agencyCode");

        Map<String, Object> soapEntry = new LinkedHashMap<>();
        soapEntry.put("assertion", "<saml:Assertion ID=\"_a1\">signature-material</saml:Assertion>");
        soapEntry.put("billingAccountNumber", "1234567890");
        soapEntry.put("policyNumber", "1234567");
        soapEntry.put("userName", "svc_billing");
        soapEntry.put("password", "s3cr3t");

        sink.emit(TracePoint.BEFORE_REQUEST, "corr-1", soapEntry);

        String line = emittedLines().get(0);
        assertThat(line)
                .doesNotContain("signature-material")
                .doesNotContain("1234567890")
                .doesNotContain("1234567")
                .doesNotContain("svc_billing")
                .doesNotContain("s3cr3t");
        assertThat(line).contains(StructuredAuditEventSink.REDACTED);
    }

    @Test // AUD-006-f
    @DisplayName("f: an unserialisable payload still emits an entry and never fails the request")
    void anUnserialisablePayloadStillEmits() {
        // NOT in tension with the audit guarantee. Constructing the audit RECORD is fatal (ADR-0017,
        // asserted in MainFunnelTest AUD-001-i); emitting a log LINE about a payload is not. A
        // Spring implementer's instinct is to make all logging non-fatal, which would silently
        // remove an audit guarantee — so the two obligations are kept apart deliberately.
        Object notSerialisable = new Object() {
            @SuppressWarnings("unused")
            public String getBoom() {
                throw new IllegalStateException("this payload cannot be rendered");
            }
        };

        assertThatCode(() -> sink.emit(TracePoint.EXCEPTION, "corr-1",
                new LinkedHashMap<>(Map.of("payload", notSerialisable))))
                .as("a logging step must never be the reason a request fails")
                .doesNotThrowAnyException();

        String line = emittedLines().get(0);
        assertThat(line)
                .as("a degraded line still carries the two fields that make it findable")
                .contains("\"tracePoint\":\"EXCEPTION\"")
                .contains("\"correlationId\":\"corr-1\"")
                .contains("serialisationError");
    }

    @Test // AUD-006-g
    @DisplayName("g: emitting a log entry alters neither the payload nor any field a later step reads")
    void emittingNeverMutatesWhatItLogs() {
        // R-005 is open: nobody knows whether the legacy's opaque entry/exit logging sub-flows mutate
        // the payload, and PAR-002-h is the capture that would answer it. What the target can
        // guarantee — and does here — is that ITS logging is free of side effects, so the question
        // never has to be asked again of this codebase.
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("assertion", "<saml:Assertion/>");
        fields.put("billingAccountNumber", "1234567890");
        fields.put("requestUri", "/externalPrimaryAccount/1234567890/account-billing");
        Map<String, Object> before = new LinkedHashMap<>(fields);

        sink.emit(TracePoint.START, "corr-1", fields);
        sink.emit(TracePoint.END, "corr-1", fields);

        assertThat(fields)
                .as("the caller's map is untouched — masking happens on a copy, on the way out")
                .isEqualTo(before);
        assertThat(fields.get("billingAccountNumber"))
                .as("a later step reading this field must see the identifier, not the mask")
                .isEqualTo("1234567890");
    }

    // ---------------------------------------------------------------------------------------------

    private static ListAppender<ILoggingEvent> attachAppender() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(context);
        appender.start();
        ch.qos.logback.classic.Logger auditLogger =
                context.getLogger("com.westfield.api.billing.audit");
        auditLogger.setLevel(Level.INFO);
        auditLogger.addAppender(appender);
        return appender;
    }

    private static void detachAppender(ListAppender<ILoggingEvent> appender) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger("com.westfield.api.billing.audit").detachAppender(appender);
        appender.stop();
    }
}
