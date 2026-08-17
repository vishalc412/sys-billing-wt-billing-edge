package com.westfield.api.billing.edge.domain.audit;

import com.westfield.api.billing.edge.testsupport.Fixtures;
import com.westfield.api.billing.platform.spi.CallerContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AUD-002 — resource path masking for the audit trail (N-0051, N-0052).
 *
 * <p>The masking rule has NO test coverage in the legacy system at all (R-020), so these are a new
 * specification of existing behaviour derived from the knowledge map's edge cases. PAR-002-c is the
 * capture that would turn them into parity evidence and is blocked; until then they say what we
 * believe the rule does, which is more than existed before and less than proof.
 */
@DisplayName("AUD-002 resource path masking")
class ResourcePathMaskerTest {

    @Test // AUD-002-a
    @DisplayName("a: an entirely alphabetic path is not masked at all")
    void alphabeticPathIsNotMasked() {
        assertThat(ResourcePathMasker.maskedTemplate("/primaryAccount/transactions"))
                .isEqualTo("/primaryAccount/transactions");
    }

    @Test // AUD-002-b
    @DisplayName("b: a 10-digit billing account number segment is replaced with X")
    void billingAccountNumberSegmentIsMasked() {
        String masked = ResourcePathMasker.maskedTemplate(
                "/externalPrimaryAccount/1234567890/account-billing");

        assertThat(masked).isEqualTo("/externalPrimaryAccount/X/account-billing");
        assertThat(masked).doesNotContain("1234567890");
    }

    @Test // AUD-002-c
    @DisplayName("c: a segment that is alphabetic once hyphens are removed is not replaced")
    void hyphenatedAlphabeticSegmentSurvives() {
        // The identifier test removes hyphens before asking whether the segment is alphabetic, so
        // 'account-billing' and 'policy-billing' pass through intact.
        assertThat(ResourcePathMasker.maskSegment("account-billing")).isEqualTo("account-billing");
        assertThat(ResourcePathMasker.maskSegment("pending-cancel-today")).isEqualTo("pending-cancel-today");
    }

    @Test // AUD-002-d
    @DisplayName("d: a purely alphabetic agency code is NOT masked — the rule is a heuristic")
    void alphabeticAgencyCodeIsNotMasked() {
        // Legacy behaviour, preserved deliberately (N-0051 ec 2). An agency code with no digits
        // reaches the audit log in full. It is a heuristic, not a guarantee, and pretending otherwise
        // by widening the rule would change every audit line that log analytics groups on.
        assertThat(ResourcePathMasker.maskedTemplate("/pastDueToday/ABCDE"))
                .isEqualTo("/pastDueToday/ABCDE");
        // A code carrying a digit is masked, which is why the exposure is partial rather than total.
        assertThat(ResourcePathMasker.maskedTemplate("/pastDueToday/A0421"))
                .isEqualTo("/pastDueToday/X");
    }

    @Test // AUD-002-e
    @DisplayName("e: an empty segment from a doubled or trailing slash is not masked")
    void emptySegmentsAreNotMasked() {
        // isAlpha("") is true in DataWeave, so the empty segment is 'alphabetic' and survives, and
        // the doubled or trailing separator survives with it (N-0052 ec 4).
        assertThat(ResourcePathMasker.maskedTemplate("/pastDueToday//A0421"))
                .isEqualTo("/pastDueToday//X");
        assertThat(ResourcePathMasker.maskedTemplate("/pastDueToday/"))
                .isEqualTo("/pastDueToday/");
    }

    @Test // AUD-002-f
    @DisplayName("f: a path with no leading separator loses its first segment rather than masking it")
    void firstSegmentIsConsumedWhenThereIsNoLeadingSeparator() {
        // substringAfter(path, "/") discards everything up to and including the FIRST slash, so a
        // path that does not start with one loses its first segment entirely (N-0052 ec 5). The
        // caller then prepends the separator (N-0051 ec 3), which is why the result still looks
        // well-formed and is quietly missing a segment.
        assertThat(ResourcePathMasker.maskedTemplate("pastDueToday/A0421")).isEqualTo("/X");
        assertThat(ResourcePathMasker.maskedTemplate("pastDueToday")).isEqualTo("/");
    }

    @Test // AUD-002-g
    @DisplayName("g: the masking function is pure — same input, same output, no configuration, no I/O")
    void maskingIsPure() throws Exception {
        String path = "/externalPrimaryAccount/1234567890/account-billing";
        String first = ResourcePathMasker.maskedTemplate(path);
        for (int i = 0; i < 100; i++) {
            assertThat(ResourcePathMasker.maskedTemplate(path)).isEqualTo(first);
        }

        // Purity asserted structurally as well as behaviourally (N-0052 ec 6): the type holds no
        // mutable state and exposes no instance method, so there is nothing for configuration or a
        // remote call to be smuggled into later.
        for (Field field : ResourcePathMasker.class.getDeclaredFields()) {
            assertThat(Modifier.isStatic(field.getModifiers()))
                    .as("field %s must be static", field.getName()).isTrue();
            assertThat(Modifier.isFinal(field.getModifiers()))
                    .as("field %s must be final", field.getName()).isTrue();
        }
        for (Method method : ResourcePathMasker.class.getDeclaredMethods()) {
            assertThat(Modifier.isStatic(method.getModifiers()))
                    .as("method %s must be static", method.getName()).isTrue();
        }
    }

    @Test // AUD-002-h
    @DisplayName("h: the audit record keeps the raw URI in full and masks only the template")
    void rawUriKeepsTheIdentifierAndOnlyTheTemplateIsMasked() {
        String rawUri = "/externalPrimaryAccount/1234567890/account-billing?environment=TEST";
        RequestResponseAuditRecord record = new RequestResponseAuditRecord(
                OffsetDateTime.parse("2026-08-16T10:00:00Z"), 1_755_338_400_000L,
                "sapi-billing", "v1", "corr-1", "interaction-1", rawUri,
                Fixtures.fullyPopulatedCaller());

        Map<String, Object> fields = record.entryFields();

        // Both fields are present, and they say different things on purpose: support needs the exact
        // URI that was called, analytics needs a groupable shape without the identifier in it.
        assertThat(fields.get("requestUri")).isEqualTo(rawUri);
        assertThat(String.valueOf(fields.get("requestUri"))).contains("1234567890");
        assertThat(String.valueOf(fields.get("requestTemplate"))).doesNotContain("1234567890");
        // The template is built from the PATH half only. Masking the query string too would make
        // every distinct query mask to its own shape and destroy the grouping the template exists
        // for, so the query is dropped from the template and survives in requestUri.
        assertThat(fields.get("requestTemplate"))
                .isEqualTo("/externalPrimaryAccount/X/account-billing");
    }

    @Test
    @DisplayName("a null path does not throw — a broken audit record must never break a request")
    void nullPathIsTolerated() {
        assertThat(ResourcePathMasker.maskedTemplate(null)).isEqualTo("/");
    }

    @Test
    @DisplayName("the EMPTY sentinel is what an absent identity field looks like")
    void sentinelIsTheContract() {
        assertThat(CallerContext.EMPTY).isEqualTo("EMPTY");
    }
}
