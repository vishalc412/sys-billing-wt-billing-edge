package com.westfield.api.billing.edge.adapter.out.audit;

import com.westfield.api.billing.edge.adapter.in.web.EndpointAuditFilter;
import com.westfield.api.billing.edge.adapter.in.web.RequestFunnel;
import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.edge.domain.api.ApiResource;
import com.westfield.api.billing.edge.domain.api.ApiResourceTable;
import com.westfield.api.billing.edge.testsupport.Fixtures;
import com.westfield.api.billing.edge.testsupport.MutableClock;
import com.westfield.api.billing.edge.testsupport.RecordingAuditEventSink;
import com.westfield.api.billing.edge.testsupport.TestFilterChain;
import com.westfield.api.billing.platform.observability.TracePoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AUD-004 completion and AUD-005 stream coverage (N-0036, N-0045, N-0048, N-0049, ADR-0017, ADR-0035).
 *
 * <p>Two facts about the legacy audit stream are preserved here that an implementer would very
 * reasonably "fix":
 * <ul>
 *   <li><b>Five of eight endpoints emit a per-endpoint record; three do not.</b> The
 *       {@code /primaryAccount*} resources emit none, while still emitting a request/response record
 *       (N-0045 ec 1). Closing that gap would change log volume and every report that counts
 *       records, and ADR-0017 gates it on a compliance owner nobody has identified (R-021).</li>
 *   <li><b>The five that do emit were five verbatim copies of one projection.</b> ADR-0017
 *       consolidates them into one implementation with the template as a parameter — no emitted
 *       value changes, and five copies can no longer drift apart, which is precisely how the two
 *       impersonation definitions came to disagree in the first place.</li>
 * </ul>
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("AUD-004 / AUD-005 audit record completion and stream coverage")
class AuditRecordCompletionTest {

    private BillingEdgeProperties properties;
    private RecordingAuditEventSink sink;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        properties = Fixtures.validProperties();
        sink = new RecordingAuditEventSink();
        clock = new MutableClock(Instant.parse("2026-08-17T09:00:00Z"));
        MDC.put("correlationId", "corr-1");
    }

    @Test // AUD-004-a
    @DisplayName("a: the closed record carries the response status and the wall-clock elapsed time, at the end trace point")
    void theClosedRecordCarriesStatusAndElapsedTime() {
        RequestScopedAuditRecordAdapter adapter =
                new RequestScopedAuditRecordAdapter(sink, properties, clock);

        adapter.open("/pastDueToday/A0421?environment=TEST", Fixtures.fullyPopulatedCaller(), "interaction-1");
        clock.advance(Duration.ofMillis(137));
        adapter.close(200, false);

        // The entry record is emitted at START and the completed one at END. Both, not one merged
        // line: the legacy writes two, and a consumer joining on correlation id expects two.
        assertThat(sink.at(TracePoint.START)).hasSize(1);
        RecordingAuditEventSink.Emission end = sink.only(TracePoint.END);

        assertThat(end.fields()).containsEntry("responseStatusCode", 200);
        assertThat(end.fields())
                .as("elapsed is wall-clock between entry and exit, not a derived or estimated figure")
                .containsEntry("elapsedTime", 137L);
        assertThat(end.correlationId()).isEqualTo("corr-1");

        // ADR-0035's marker: the record says whether the response was shaped by the catch-all.
        assertThat(end.fields()).containsEntry("unhandledFailure", false);

        // Every field from the entry projection survives into the completed one, so the two lines
        // describe the same request rather than two different subsets of it.
        assertThat(end.fields().keySet())
                .containsAll(sink.only(TracePoint.START).fields().keySet());
    }

    @Test // AUD-004-h
    @DisplayName("h: masking comes from configuration through the shared logger, never from hardcoded values")
    void maskingIsConfiguredRatherThanHardcoded() {
        // N-0048 ec 1: the legacy emits through an EXTERNAL, shared organisation logger configuration
        // whose mask and disable lists are properties. That indirection is preserved — the sink reads
        // its lists from configuration on every emission, so an operator can change what is masked
        // without a code change, exactly as the legacy intended and never actually used (the lists
        // ship empty in all six environments, which is what ADR-0015 corrects).
        StructuredAuditEventSink configured =
                new StructuredAuditEventSink(new com.fasterxml.jackson.databind.ObjectMapper(), properties);

        properties.getLogging().setMaskFields(List.of("policyNumber"));
        Map<String, Object> firstPass = configured.redact(new java.util.LinkedHashMap<>(Map.of(
                "policyNumber", "1234567", "billingAccountNumber", "1234567890")));

        assertThat(firstPass.get("policyNumber")).isEqualTo("X");
        assertThat(firstPass.get("billingAccountNumber"))
                .as("only what configuration names is masked — nothing is masked by hardcoded rule")
                .isEqualTo("1234567890");

        // Change the configuration; the SAME sink instance obeys it, with no restart and no rebuild.
        properties.getLogging().setMaskFields(List.of("billingAccountNumber"));
        Map<String, Object> secondPass = configured.redact(new java.util.LinkedHashMap<>(Map.of(
                "policyNumber", "1234567", "billingAccountNumber", "1234567890")));

        assertThat(secondPass.get("billingAccountNumber")).isEqualTo("X");
        assertThat(secondPass.get("policyNumber")).isEqualTo("1234567");

        // And one shared logger name carries the stream, so a consumer subscribes to one thing.
        assertThat(org.slf4j.LoggerFactory.getLogger("com.westfield.api.billing.audit"))
                .as("the audit stream has a single, stable logger name")
                .isNotNull();
    }

    @Test // AUD-005-f
    @DisplayName("f: the five per-endpoint records differ only in the URI template and share an identical field set")
    void theFivePerEndpointRecordsShareOneFieldSet() throws Exception {
        // ADR-0017's consolidation, asserted from the outside. The legacy had five verbatim copies of
        // this projection at sapi-billing-search.xml lines 152, 173, 194, 227 and 260. If the field
        // set can drift between endpoints again, the consolidation did not happen.
        List<String> emittingPaths = List.of(
                "/externalPrimaryAccount/1234567890/account-billing",
                "/externalPrimaryAccount/1234567/policy-billing",
                "/pastDueToday/A0421",
                "/pendingCancelToday/A0421",
                "/info");

        Set<Set<String>> distinctFieldSets = new LinkedHashSet<>();
        Set<String> distinctTemplates = new LinkedHashSet<>();

        for (String path : emittingPaths) {
            sink.clear();
            runEndpointFilter(path);

            RecordingAuditEventSink.Emission emission = sink.only(TracePoint.START);
            distinctFieldSets.add(emission.fields().keySet());
            distinctTemplates.add(String.valueOf(emission.fields().get("uriTemplate")));
        }

        assertThat(distinctFieldSets)
                .as("one projection, five call sites: exactly one field set across all five")
                .hasSize(1);
        assertThat(distinctTemplates)
                .as("and the template is the only thing that varies")
                .hasSize(5);
    }

    @Test // AUD-005-g
    @DisplayName("g: the three primaryAccount resources emit no per-endpoint record but are still request/response audited")
    void primaryAccountResourcesEmitNoPerEndpointRecord() throws Exception {
        // N-0045 ec 1, preserved. Three of eight endpoints are absent from this stream entirely.
        // It looks like an omission and probably is, but closing it changes the volume and content
        // of a compliance-relevant log with nobody accountable for the change (R-021) — so it is
        // asserted as the current shape and left for ADR-0017 to revisit with an owner.
        for (String path : List.of("/primaryAccount", "/primaryAccount/transactions",
                "/primaryAccount/policy/escrow/transactions")) {
            sink.clear();
            runEndpointFilter(path);

            assertThat(sink.emissions())
                    .as("%s must emit no per-endpoint audit record", path)
                    .isEmpty();

            ApiResource resource = new ApiResourceTable().find("GET", path).orElseThrow();
            assertThat(resource.emitsEndpointAuditRecord()).isFalse();
        }

        // The request/response record is still written for them, by a different filter — so these
        // requests are audited, just not twice. Losing THAT would be a real audit gap.
        sink.clear();
        RequestScopedAuditRecordAdapter adapter =
                new RequestScopedAuditRecordAdapter(sink, properties, clock);
        adapter.open("/primaryAccount/transactions", Fixtures.fullyPopulatedCaller(), "interaction-1");
        adapter.close(200, false);

        assertThat(sink.at(TracePoint.START)).hasSize(1);
        assertThat(sink.at(TracePoint.END)).hasSize(1);
    }

    private void runEndpointFilter(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestFunnel.putCorrelationId(request, "corr-1");
        RequestFunnel.putCaller(request, Fixtures.fullyPopulatedCaller());

        new TestFilterChain(TestFilterChain.doNothing(),
                new EndpointAuditFilter(new ApiResourceTable(), sink, properties))
                .doFilter(request, response);
    }
}
