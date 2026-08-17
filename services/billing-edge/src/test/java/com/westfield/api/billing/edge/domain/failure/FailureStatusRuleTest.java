package com.westfield.api.billing.edge.domain.failure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westfield.api.billing.edge.adapter.in.web.FailureResponseWriter;
import com.westfield.api.billing.edge.adapter.in.web.RequestFunnel;
import com.westfield.api.billing.edge.adapter.out.fault.SoapBillingFaultClassifier;
import com.westfield.api.billing.edge.application.failure.UnhandledFailurePresenter;
import com.westfield.api.billing.platform.spi.BillingFaultClassifier;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ERR-002 — failure status derivation and upstream status relaying (N-0068, N-0070).
 *
 * <p>ADR-0033 decided PRESERVE. The back end's status is still relayed verbatim, so a caller can
 * still receive a 401 that describes our service account rather than their credentials, and a 404 on
 * an API resource that plainly exists. What the migration adds is the {@code X-Billing-Failure-Origin}
 * header and two counters, so the misleading relay becomes MEASURABLE before anyone argues about
 * changing it. No consumer inventory exists (R-019), which is precisely why the argument cannot be
 * settled today.
 */
@DisplayName("ERR-002 failure status derivation")
class FailureStatusRuleTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final FailureResponseWriter writer =
            new FailureResponseWriter(new ObjectMapper(), meterRegistry);
    private final UnhandledFailurePresenter presenter = new UnhandledFailurePresenter();

    private MockHttpServletResponse relay(Object upstreamStatus) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/pastDueToday/A0421");
        MockHttpServletResponse response = new MockHttpServletResponse();
        writer.write(request, response,
                presenter.present(new IllegalStateException("backend said no"), "corr-1", upstreamStatus));
        return response;
    }

    @Test // ERR-002-a
    @DisplayName("a: the status the back end gave is the status the caller receives")
    void backendStatusIsRelayedVerbatim() throws Exception {
        assertThat(FailureStatusRule.statusFor(502)).isEqualTo(502);
        assertThat(FailureStatusRule.statusFor("503")).isEqualTo(503);
        assertThat(relay(502).getStatus()).isEqualTo(502);
    }

    @Test // ERR-002-b
    @DisplayName("b: a failure carrying no HTTP status at all is 500")
    void noUpstreamStatusIsFiveHundred() throws Exception {
        // A mapping failure or a refused connection has no status to relay. The legacy
        // 'as Number default 500' fires only on null, and this is that case.
        assertThat(FailureStatusRule.statusFor(null)).isEqualTo(500);
        assertThat(relay(null).getStatus()).isEqualTo(500);
    }

    @Test // ERR-002-c
    @DisplayName("c: a backend 401 or 403 reaches the caller unchanged and is counted as a relayed auth status")
    void relayedAuthStatusesAreCounted() throws Exception {
        MockHttpServletResponse unauthorized = relay(401);
        MockHttpServletResponse forbidden = relay(403);

        assertThat(unauthorized.getStatus()).isEqualTo(401);
        assertThat(forbidden.getStatus()).isEqualTo(403);
        assertThat(meterRegistry.counter("mule.parity.relayed_upstream_auth_status", "status", "401").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.counter("mule.parity.relayed_upstream_auth_status", "status", "403").count())
                .isEqualTo(1.0);

        // The additive header is what tells the caller whose credentials were actually rejected —
        // the one thing the relayed status cannot say (ADR-0033).
        assertThat(unauthorized.getHeader(RequestFunnel.FAILURE_ORIGIN_HEADER)).isNotBlank();
    }

    @Test // ERR-002-d
    @DisplayName("d: a backend 404 becomes a 404 on an API resource that exists, and is counted")
    void relayedNotFoundIsCounted() throws Exception {
        MockHttpServletResponse response = relay(404);

        // Legacy behaviour (N-0068 defect), preserved. It is genuinely misleading — the resource is
        // declared, routed and implemented; it is the ACCOUNT that does not exist — and the counter
        // is what will let someone argue for changing it with evidence rather than with an opinion.
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(meterRegistry.counter("mule.parity.relayed_upstream_not_found").count()).isEqualTo(1.0);
    }

    @Test // ERR-002-e
    @DisplayName("e: a non-numeric backend status completes with 500 rather than raising inside failure handling")
    void nonNumericStatusDoesNotRaiseASecondFailure() throws Exception {
        // A NARROW, DELIBERATE CORRECTION. The legacy 'as Number' would raise here, inside an error
        // handler that has no further handler of its own — turning a backend oddity into an unhandled
        // failure. Answering 500 is the outcome the legacy expression was reaching for anyway.
        assertThat(FailureStatusRule.statusFor("not-a-number")).isEqualTo(500);
        assertThat(FailureStatusRule.statusFor("")).isEqualTo(500);
        assertThat(relay("not-a-number").getStatus()).isEqualTo(500);
    }

    @Test // ERR-002-f
    @DisplayName("f: a backend 200 carrying a fault body triggers no status derivation at all")
    void twoHundredWithAFaultBodyIsNotAFailurePath() throws Exception {
        // No HTTP-level failure was raised, so the failure path is never entered and no status is
        // derived. What happens instead is the endpoint's own fault handling, which classifies the
        // body and chooses its own outcome (ADR-0011, ADR-0027).
        BillingFaultClassifier classifier = new SoapBillingFaultClassifier(meterRegistry);

        BillingFaultClassifier.Classification classification = classifier.classify(
                "<fault><errorCode>204</errorCode><faultstring>Account not found</faultstring></fault>");

        assertThat(classification).isEqualTo(BillingFaultClassifier.Classification.ACCOUNT_NOT_FOUND);
        assertThat(meterRegistry.counter("mule.parity.relayed_upstream_auth_status", "status", "401").count())
                .isEqualTo(0.0);
        assertThat(meterRegistry.counter("mule.parity.relayed_upstream_not_found").count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("the auth-status predicate covers exactly 401 and 403")
    void relayedAuthStatusPredicateIsNarrow() {
        assertThat(FailureStatusRule.isRelayedAuthStatus(401)).isTrue();
        assertThat(FailureStatusRule.isRelayedAuthStatus(403)).isTrue();
        assertThat(FailureStatusRule.isRelayedAuthStatus(400)).isFalse();
        assertThat(FailureStatusRule.isRelayedAuthStatus(500)).isFalse();
    }
}
