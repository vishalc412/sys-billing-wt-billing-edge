package com.westfield.api.billing.edge.application.failure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westfield.api.billing.edge.adapter.in.web.FailureResponseWriter;
import com.westfield.api.billing.edge.adapter.in.web.RequestFunnel;
import com.westfield.api.billing.edge.domain.failure.AbsorbedFailureBody;
import com.westfield.api.billing.edge.testsupport.ParityBaselines;
import com.westfield.api.billing.platform.errors.AbsorbedFailureHandler;
import com.westfield.api.billing.platform.errors.FailureOrigin;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ERR-001 — uniform failure body construction (N-0068, N-0069).
 * ERR-003 — the fallback handler and the shared error-handler boundary (N-0006, N-0012, N-0016, N-0030).
 */
@DisplayName("ERR-001 / ERR-003 failure presentation")
class FailurePresentationTest {

    private final UnhandledFailurePresenter presenter = new UnhandledFailurePresenter();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test // ERR-001-a
    @DisplayName("a: the failure body names this API as fault actor and carries description, type, cause and correlation id")
    void failureBodyCarriesTheFiveFields() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/pastDueToday/A0421");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FailureResponseWriter writer = new FailureResponseWriter(objectMapper, meterRegistry);

        writer.write(request, response, presenter.present(
                new IllegalStateException("ESB call failed", new java.net.ConnectException("refused")),
                "corr-42", 502));

        Map<?, ?> body = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertThat(body.get("faultActor")).isEqualTo(AbsorbedFailureBody.FAULT_ACTOR);
        assertThat(body.get("errorDesc")).isEqualTo("ESB call failed");
        assertThat(body.get("errorType")).isNotNull();
        assertThat(body.get("errorCause")).isNotNull();
        assertThat(body.get("correlationId")).isEqualTo("corr-42");
    }

    @Test // ERR-001-b
    @DisplayName("b: an absent cause is a present key with a null value, never an omitted key")
    void absentCauseIsANullValuedKey() {
        // N-0069 ec 4: no field in this body has a default, so the key set is identical on every
        // failure. A consumer reading body.errorCause gets null, not a KeyError.
        Map<String, Object> body = new AbsorbedFailureBody(
                AbsorbedFailureBody.FAULT_ACTOR, "something broke",
                new AbsorbedFailureBody.ErrorType("MULE", "EXPRESSION"), null, "corr-1").asMap();

        assertThat(body).containsKey("errorCause");
        assertThat(body.get("errorCause")).isNull();
    }

    @Test // ERR-001-c
    @DisplayName("c: the emitted body deliberately does not match the fault type the contract publishes")
    void emittedBodyDivergesFromThePublishedFaultType() {
        // ADR-0042 PRESERVE. The legacy RAML published commonError; the application has never emitted
        // it. The contract was rewritten to match the code, not the other way round. This assertion
        // is the record of that divergence — without it, someone eventually "fixes" the code to match
        // a published type no consumer has ever received.
        List<String> publishedFaultType = List.of(
                "faultActor", "faultCode", "faultMessage", "faultDetail", "faultTime", "innerFault");

        Map<String, Object> emitted = new AbsorbedFailureBody(
                AbsorbedFailureBody.FAULT_ACTOR, "d", null, null, "corr-1").asMap();

        assertThat(emitted.keySet()).containsExactly(
                "faultActor", "errorDesc", "errorType", "errorCause", "correlationId");
        assertThat(emitted.keySet()).doesNotContain("faultCode", "faultMessage", "faultDetail",
                "faultTime", "innerFault");
        assertThat(emitted.keySet()).isNotEqualTo(publishedFaultType);
    }

    @Test // ERR-001-d
    @DisplayName("d: a structured error type stays nested and is not flattened to a string")
    void errorTypeStaysNested() {
        // N-0069 ec 3. The Mule error type serialises as namespace plus identifier. Flattening it to
        // "MULE:EXPRESSION" would be tidier and would change what every consumer parses.
        Map<String, Object> body = new AbsorbedFailureBody(
                AbsorbedFailureBody.FAULT_ACTOR, "d",
                new AbsorbedFailureBody.ErrorType("MULE", "EXPRESSION"), null, "corr-1").asMap();

        assertThat(body.get("errorType")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> errorType = (Map<String, Object>) body.get("errorType");
        assertThat(errorType).containsEntry("namespace", "MULE").containsEntry("identifier", "EXPRESSION");
    }

    @Test // ERR-001-e
    @DisplayName("e: INVERTED per ADR-0015 — the response body keeps the raw cause; only the LOG is masked")
    void rawCauseStaysInTheBodyAndMaskingAppliesToTheLog() {
        // WP-001 wrote this criterion against ADR-CANDIDATE-0015 option B, which would have redacted
        // the cause in the RESPONSE. The accepted ADR-0015 is narrower and says so in its title:
        // "Logs are masked; the response body is not changed". Changing a response body is a contract
        // change and belongs to ADR-0042, not to a logging decision — so the criterion inverts.
        //
        // The exposure this criterion was worried about is real: error.cause can carry internal
        // hostnames and fragments of the SOAP payload (N-0069 ec 2). It is now carried on the LOGGING
        // path only, where ADR-0015's masking applies, and the body is unchanged.
        String causeWithInternalDetail =
                "java.net.ConnectException: tst2esb.westfieldgrp.com:8443 refused";
        Map<String, Object> body = new AbsorbedFailureBody(
                AbsorbedFailureBody.FAULT_ACTOR, "ESB call failed", null,
                causeWithInternalDetail, "corr-1").asMap();

        assertThat(body.get("errorCause"))
                .as("ADR-0015 explicitly leaves the response body alone")
                .isEqualTo(causeWithInternalDetail);
    }

    @Test // ERR-001-f
    @DisplayName("f: the status is derived after the body is built and is unaffected by having built it")
    void statusDerivationSurvivesTheBodyBeingBuiltFirst() {
        // N-0068 ec 2. The legacy replaces the payload FIRST and derives the status SECOND from the
        // error ATTRIBUTES, which the payload replacement does not disturb. Reordering the two
        // silently breaks the derivation, so the order is explicit in the presenter and asserted here.
        UnhandledFailurePresenter.Presentation withStatus =
                presenter.present(new IllegalStateException("boom"), "corr-1", 502);
        UnhandledFailurePresenter.Presentation withoutStatus =
                presenter.present(new IllegalStateException("boom"), "corr-1", null);

        assertThat(withStatus.status()).isEqualTo(502);
        assertThat(withoutStatus.status()).isEqualTo(500);
        // The body is identical either way: building it neither consumed nor altered the attributes.
        assertThat(withStatus.body()).isEqualTo(withoutStatus.body());
        assertThat(withStatus.origin()).isEqualTo(FailureOrigin.UPSTREAM_ESB);
        assertThat(withoutStatus.origin()).isEqualTo(FailureOrigin.THIS_SERVICE);
    }

    @Test // ERR-001-g
    @DisplayName("g: a failure inside failure-body construction answers with our own status and is counted")
    void aFailureInsideFailureHandlingIsContainedAndCounted() throws Exception {
        // A body Jackson cannot serialise: a map that contains itself. In the legacy this raises
        // inside a handler that has no further handler, so the caller receives an unhandled runtime
        // error. Here it is contained, attributed to this service, and counted.
        Map<String, Object> cyclic = new LinkedHashMap<>();
        cyclic.put("self", cyclic);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/pastDueToday/A0421");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new FailureResponseWriter(objectMapper, meterRegistry).write(request, response,
                new UnhandledFailurePresenter.Presentation(502, cyclic, FailureOrigin.UPSTREAM_ESB));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getHeader(RequestFunnel.FAILURE_ORIGIN_HEADER)).isEqualTo("this-service");
        assertThat(meterRegistry.find("billing.failure.presentation_failed").counters())
                .isNotEmpty();
    }

    @Test // ERR-003-a
    @DisplayName("a: a failure in a path with no handler of its own is shaped by the organisation-wide presentation")
    void unhandledFailuresAreShapedByTheSharedPresentation() {
        // N-0006: any flow that declares no error handler falls back to the global one. That is
        // behaviour that appears in NO individual flow definition, which is exactly why it needs a
        // test — nobody reading a flow can see it.
        UnhandledFailurePresenter.Presentation presentation =
                presenter.present(new RuntimeException("nothing caught this"), "corr-1", null);

        assertThat(presentation.status()).isEqualTo(500);
        assertThat(presentation.body()).containsKeys(
                "faultActor", "errorDesc", "errorType", "errorCause", "correlationId");
    }

    @Test // ERR-003-b
    @DisplayName("b: runtime, mapping and connector failures are all handled identically")
    void everyFailureClassIsHandledTheSameWay() {
        // N-0012: the fallback matches type ANY. Three quite different failures, one outcome — which
        // is why nothing downstream can tell them apart, and why the counters matter more than the
        // shape.
        List<Throwable> everyClass = List.of(
                new RuntimeException("runtime"),
                new IllegalArgumentException("mapping"),
                new java.net.ConnectException("connector"));

        List<Map<String, Object>> shapes = everyClass.stream()
                .map(failure -> presenter.present(failure, "corr-1", null).body())
                .toList();

        for (Map<String, Object> shape : shapes) {
            assertThat(shape.keySet()).isEqualTo(shapes.get(0).keySet());
        }
        for (Throwable failure : everyClass) {
            assertThat(presenter.present(failure, "corr-1", null).status()).isEqualTo(500);
        }
    }

    @Test // ERR-003-d
    @DisplayName("d: a backend failure inside an endpoint is absorbed there and never reaches the catch-all")
    void endpointsAbsorbTheirOwnBackendFailures() {
        // Every backend-calling flow in the legacy is wrapped in on-error-continue, so no failure
        // propagates and the HTTP status is the only signal (N-0068, N-0084). Reproduced exactly for
        // DOWNSTREAM failures, and now counted for the first time.
        DefaultAbsorbedFailureHandler handler = new DefaultAbsorbedFailureHandler(meterRegistry);

        String legacyOutcome = handler.absorb("CAP-009",
                () -> {
                    throw downstreamFailure();
                },
                "the outcome the legacy returned");

        assertThat(legacyOutcome).isEqualTo("the outcome the legacy returned");
        assertThat(meterRegistry.find("mule.parity.swallowed_error").counters()).isNotEmpty();
    }

    @Test // ERR-003-f
    @DisplayName("f: a shared unit invoked from two callers takes each caller's own failure handling")
    void sharedUnitsInheritTheCallersHandling() {
        // N-0006 ec: a sub-flow has no error handling of its own, so the SAME shared unit behaves
        // differently depending on who called it. Reproduced structurally: the absorb() decision is
        // made by the caller, which supplies its own legacy outcome, not by the shared unit.
        DefaultAbsorbedFailureHandler handler = new DefaultAbsorbedFailureHandler(meterRegistry);
        AbsorbedFailureHandler.ThrowingSupplier<String> oneSharedUnit = () -> {
            throw downstreamFailure();
        };

        assertThat(handler.absorb("CAP-009", oneSharedUnit, "caller A answers 204")).isEqualTo("caller A answers 204");
        assertThat(handler.absorb("CAP-010", oneSharedUnit, "caller B answers an empty list"))
                .isEqualTo("caller B answers an empty list");
    }

    @Test // ERR-003-h
    @DisplayName("h: the organisation-wide presentation is REPORTED AS BLOCKED, not silently assumed correct")
    void theSharedErrorHandlerContractIsReportedAsBlocked() {
        // The mechanical form of hard rule 7. westfield-common-errorhandler.xml lives in an Exchange
        // module that is not in the source tree; the knowledge map calls it "the single largest
        // behavioural blind spot in the application" (R-013). ADR-0016 implements it behind an
        // interface with the assumption stated IN CODE rather than blocking six capabilities on an
        // email — and this flag is what stops the assumption quietly becoming a fact.
        assertThat(UnhandledFailurePresenter.ASSUMED_CONTRACT)
                .as("flip this to false only when the module owner has supplied the real contract")
                .isTrue();
        assertThat(UnhandledFailurePresenter.ASSUMPTION_RISK).isEqualTo("R-013");

        // And no 597/598/599 mapping is invented. If the shared handler emits those for timeouts they
        // are live behaviour a consumer may branch on, and guessing at them is worse than omitting
        // them (ADR-0042).
        for (int declaredButUnmapped : UnhandledFailurePresenter.UNMAPPED_DECLARED_TIMEOUT_STATUSES) {
            assertThat(presenter.present(new RuntimeException("timeout"), "corr-1", null).status())
                    .isNotEqualTo(declaredButUnmapped);
        }

        assertThat(ParityBaselines.isVerifiedParity("out/golden/ERR-003/unhandled-failure-responses"))
                .as("ERR-003-g needs a captured baseline that does not exist (R-013, R-025)")
                .isFalse();
    }

    @Test
    @DisplayName("our own failures are NOT absorbed — they surface as 500 so our bugs stay visible")
    void ourOwnFailuresAreNotAbsorbed() {
        // The one deliberate divergence in ADR-0010. In the legacy our bug and the back end's bug are
        // indistinguishable to a caller, which would hide our own defects during cutover — when they
        // are both most likely and most correctable.
        DefaultAbsorbedFailureHandler handler = new DefaultAbsorbedFailureHandler(meterRegistry);

        assertThatThrownBy(() -> handler.absorb("CAP-009",
                () -> {
                    throw new IllegalStateException("a bug in our own mapper");
                },
                "must not be returned"))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * A failure whose top frame is in an {@code adapter.out} package, which is how ADR-0010 defines
     * "came off the wire" rather than "came out of our rules".
     */
    private static RuntimeException downstreamFailure() {
        RuntimeException failure = new IllegalStateException("the back end failed");
        failure.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.westfield.api.billing.edge.adapter.out.esb.EsbClient",
                        "call", "EsbClient.java", 42)});
        return failure;
    }
}
