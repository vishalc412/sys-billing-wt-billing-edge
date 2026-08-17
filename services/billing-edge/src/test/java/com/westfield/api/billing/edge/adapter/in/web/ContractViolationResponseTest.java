package com.westfield.api.billing.edge.adapter.in.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westfield.api.billing.edge.adapter.out.fault.SoapBillingFaultClassifier;
import com.westfield.api.billing.edge.domain.api.ApiResourceTable;
import com.westfield.api.billing.edge.domain.api.ContractViolation;
import com.westfield.api.billing.edge.domain.api.InboundAdmissionRule;
import com.westfield.api.billing.platform.spi.BillingFaultClassifier;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADM-003 — the wire form of the six contract-violation responses (N-0024 … N-0029, ADR-0042).
 *
 * <p>{@link InboundAdmissionRuleTest} asserts WHICH violation each condition produces; this asserts
 * what actually reaches the caller.
 */
@DisplayName("ADM-003 contract-violation responses on the wire")
class ContractViolationResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ContractViolationWriter writer = new ContractViolationWriter(objectMapper);

    private MockHttpServletResponse write(ContractViolation violation) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        writer.write(response, violation);
        return response;
    }

    @Test // ADM-003-c
    @DisplayName("c: the 405 carries no Allow header, which HTTP requires and the legacy omits")
    void methodNotAllowedCarriesNoAllowHeader() throws Exception {
        MockHttpServletResponse response = write(ContractViolation.METHOD_NOT_ALLOWED);

        // N-0026 ec, preserved. A client library that branches on Allow gets nothing today and must
        // continue to get nothing; adding the header would be a contract change made by accident.
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isNull();
        assertThat(response.getContentAsString()).isEqualTo("{\"message\":\"Method not allowed\"}");
    }

    @Test // ADM-003-g
    @DisplayName("g: every violation body is exactly one field and never the published fault type")
    void everyBodyIsTheSingleFieldShape() throws Exception {
        for (ContractViolation violation : ContractViolation.values()) {
            MockHttpServletResponse response = write(violation);

            assertThat(response.getStatus()).isEqualTo(violation.status());
            assertThat(response.getContentType()).startsWith("application/json");

            Map<String, Object> body =
                objectMapper.readValue(response.getContentAsString(), new TypeReference<>() {
                });
            assertThat(body).hasSize(1).containsEntry("message", violation.message());
        }
    }

    @Test // ADM-003-h
    @DisplayName("h: a business-level account-not-found is not a routing 404 — the two are different conditions")
    void businessNotFoundIsNotARoutingNotFound() {
        // A well-formed request to a declared resource is ADMITTED. Whatever the back end then says
        // about the account is the endpoint's business, and the endpoint answers 204 or 200-with-an
        // -empty-list depending on which one it is (ADR-0011, ADR-0027). Routing-level 404 means
        // "the contract declares no such resource" and is decided here, before any backend call.
        InboundAdmissionRule rule = new InboundAdmissionRule(new ApiResourceTable());

        assertThat(rule.admit("GET", "/externalPrimaryAccount/9999999999/account-billing",
                Map.of("environment", List.of("TEST")), "application/json", null, "Bearer t"))
                .as("a well-formed request is admitted whatever the account turns out to be")
                .isEmpty();

        // And the classifier that recognises the backend's not-found fault produces a classification,
        // never a status: folding a 404 into it would be the fifth copy of the drifted expression
        // ADR-0011 exists to prevent.
        BillingFaultClassifier classifier = new SoapBillingFaultClassifier(new SimpleMeterRegistry());
        BillingFaultClassifier.Classification classification = classifier.classify(
                "<fault><errorCode>204</errorCode><faultstring>Account not found</faultstring></fault>");

        assertThat(classification).isEqualTo(BillingFaultClassifier.Classification.ACCOUNT_NOT_FOUND);
        // The classifier answers with a CLASSIFICATION and never with a status: three outcomes, none
        // of which is an HTTP code. /primaryAccount answers 204, /primaryAccount/transactions answers
        // 200 with an empty list and escrow answers 204 — three different outcomes chosen by three
        // different callers from this one classification (ADR-0027).
        assertThat(BillingFaultClassifier.Classification.values()).containsExactly(
                BillingFaultClassifier.Classification.ACCOUNT_NOT_FOUND,
                BillingFaultClassifier.Classification.OTHER_FAULT,
                BillingFaultClassifier.Classification.UNPARSEABLE);
    }

    @Test
    @DisplayName("the classifier is exact-case and assumes XML, exactly as today")
    void classifierIsDeliberatelyBrittle() {
        BillingFaultClassifier classifier = new SoapBillingFaultClassifier(new SimpleMeterRegistry());

        // Widening the test — case-insensitive matching, a looser substring — would convert some
        // currently-visible faults into silent empty results. ADR-0011 rejected that explicitly.
        assertThat(classifier.classify(
                "<fault><errorCode>204</errorCode><faultstring>account not found</faultstring></fault>"))
                .isEqualTo(BillingFaultClassifier.Classification.OTHER_FAULT);
        assertThat(classifier.classify("not xml at all"))
                .isEqualTo(BillingFaultClassifier.Classification.UNPARSEABLE);
        assertThat(classifier.classify(null))
                .isEqualTo(BillingFaultClassifier.Classification.UNPARSEABLE);
    }
}
