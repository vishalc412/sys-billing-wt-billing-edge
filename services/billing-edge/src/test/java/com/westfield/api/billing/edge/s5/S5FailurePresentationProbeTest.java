package com.westfield.api.billing.edge.s5;

import com.westfield.api.billing.edge.application.failure.UnhandledFailurePresenter;
import com.westfield.api.billing.platform.errors.FailureOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The failure body, checked against the ACCEPTED ADRs rather than against the ADR CANDIDATES the task
 * criteria were written from.
 *
 * <p>ERR-001-e says: "the cause is redacted to a correlation-id-referenced diagnostic and the full
 * cause appears only in the service log", citing ADR-CANDIDATE-0015. The accepted ADR-0015 decided the
 * opposite — "the response body is unchanged, including {@code error.cause}" — and explicitly rejected
 * redacting it (rejected alternative C, deferred to ADR-0042 v2). The implementation follows the
 * accepted ADR. The criterion, never amended, therefore fails against the shipped code. That is a
 * specification defect, not an implementation one, and DEF-0107 records it as such.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("S5 — the failure presentation against the accepted ADRs")
class S5FailurePresentationProbeTest {

    private final UnhandledFailurePresenter presenter = new UnhandledFailurePresenter();

    @Test // ERR-001-e — DEF-0107
    @DisplayName("ERR-001-e: the raw cause reaches the CALLER unredacted, exactly as accepted ADR-0015 requires")
    void theRawCauseIsReturnedToTheCallerUnredacted() {
        Throwable cause = new IllegalStateException(
                "connect timed out to wassvcu.westfieldgrp.corp:9443 while reading <acct>1234567890</acct>");
        Throwable failure = new RuntimeException("backend call failed", cause);

        UnhandledFailurePresenter.Presentation presentation =
                presenter.present(failure, "corr-1234", null);

        String errorCause = String.valueOf(presentation.body().get("errorCause"));
        assertThat(errorCause)
                .as("ERR-001-e asserts redaction to a correlation-id-referenced diagnostic. The body "
                        + "carries the internal hostname and a payload fragment verbatim. ADR-0015 "
                        + "SANCTIONS this; the criterion contradicts the accepted ADR — DEF-0107.")
                .contains("wassvcu.westfieldgrp.corp")
                .contains("1234567890");
        assertThat(presentation.body().get("correlationId")).isEqualTo("corr-1234");
    }

    @Test // ERR-001-a, ERR-001-b, ERR-001-d
    @DisplayName("ERR-001-a/b/d: faultActor, errorDesc, nested errorType, null-valued errorCause and the correlation id")
    void theFailureBodyKeepsItsLegacyShape() {
        UnhandledFailurePresenter.Presentation withoutCause =
                presenter.present(new RuntimeException("mapping failed"), "corr-9", null);

        Map<String, Object> body = withoutCause.body();
        assertThat(body.keySet())
                .as("key ORDER is observable in the emitted JSON and is part of the shape")
                .containsExactly("faultActor", "errorDesc", "errorType", "errorCause", "correlationId");
        assertThat(body).containsEntry("faultActor", "sapi-billing");
        assertThat(body).containsEntry("errorDesc", "mapping failed");
        assertThat(body).containsEntry("errorCause", null);
        assertThat(body.containsKey("errorCause"))
                .as("present with a null VALUE, never omitted (N-0069 ec 4)").isTrue();
        assertThat(body.get("errorType")).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) body.get("errorType")).keySet().stream().map(String::valueOf).toList())
                .containsExactly("namespace", "identifier");
    }

    @Test // ERR-001-c, ADM-003-g
    @DisplayName("ERR-001-c: the emitted body is NOT the multi-field commonError type the legacy RAML published")
    void theEmittedBodyIsNotThePublishedFaultType() {
        Map<String, Object> body = presenter.present(new RuntimeException("x"), "c", null).body();

        assertThat(body).doesNotContainKeys("faultCode", "faultMessage", "faultDetail", "faultTime",
                "innerFault");
    }

    @Test // ERR-002-a, ERR-002-b, ERR-002-e
    @DisplayName("ERR-002-a/b/e: the backend status is relayed verbatim, absent becomes 500, non-numeric becomes 500")
    void statusDerivationFollowsAdr0033() {
        assertThat(presenter.present(new RuntimeException("x"), "c", 404).status()).isEqualTo(404);
        assertThat(presenter.present(new RuntimeException("x"), "c", 401).status()).isEqualTo(401);
        assertThat(presenter.present(new RuntimeException("x"), "c", 403).status()).isEqualTo(403);
        assertThat(presenter.present(new RuntimeException("x"), "c", null).status()).isEqualTo(500);
        assertThat(presenter.present(new RuntimeException("x"), "c", "not-a-number").status())
                .isEqualTo(500);
    }

    @Test // ERR-002-c, ADR-0033
    @DisplayName("ERR-002-c: the failure origin is attributable even though the status is relayed")
    void theFailureOriginIsRecorded() {
        assertThat(presenter.present(new RuntimeException("x"), "c", 401).origin())
                .isEqualTo(FailureOrigin.UPSTREAM_ESB);
        assertThat(presenter.present(new RuntimeException("x"), "c", null).origin())
                .isEqualTo(FailureOrigin.THIS_SERVICE);
    }

    @Test // ERR-003-h, ADR-0016
    @DisplayName("ERR-003-h: the presenter still declares itself an ASSUMED contract, blocked on R-013")
    void theUnhandledFailurePresenterIsStillAnAssumedContract() {
        assertThat(UnhandledFailurePresenter.ASSUMED_CONTRACT).isTrue();
        assertThat(UnhandledFailurePresenter.ASSUMPTION_RISK).isEqualTo("R-013");
        assertThat(UnhandledFailurePresenter.UNMAPPED_DECLARED_TIMEOUT_STATUSES)
                .as("ADR-0042: no 597/598/599 mapping is invented")
                .containsExactly(597, 598, 599);
        for (int declaredButNeverEmitted : UnhandledFailurePresenter.UNMAPPED_DECLARED_TIMEOUT_STATUSES) {
            assertThat(presenter.present(new RuntimeException("timeout"), "c", null).status())
                    .isNotEqualTo(declaredButNeverEmitted);
        }
    }
}
