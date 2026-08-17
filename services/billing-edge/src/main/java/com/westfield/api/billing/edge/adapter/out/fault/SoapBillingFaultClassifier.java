package com.westfield.api.billing.edge.adapter.out.fault;

import com.westfield.api.billing.platform.observability.MigratedFrom;
import com.westfield.api.billing.platform.spi.BillingFaultClassifier;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * The single implementation of the legacy "account not found" fault test (ADR-0011, N-0084 ec 3).
 *
 * <p>The legacy has FOUR copies of this expression and two of them have already drifted in their
 * outcome. Here there is one classification; the three different outcomes stay with the callers that
 * own them ({@code /primaryAccount} answers 204, {@code /primaryAccount/transactions} answers 200
 * with an empty list, escrow answers 204). Folding the outcome into the classifier would be the
 * fifth copy.
 *
 * <p>The test is deliberately EXACT-CASE and deliberately assumes an XML fault body: error code
 * {@code "204"} and a fault string containing {@code "Account not found"}. It is brittle, and
 * widening it — case-insensitive matching, a looser substring — is a behaviour change that ADR-0011
 * rejected explicitly: it would convert some currently-visible faults into silent empty results.
 */
@Component
@MigratedFrom(value = "km:node/N-0084", note = "the account-not-found string match, implemented once (ADR-0011)")
public class SoapBillingFaultClassifier implements BillingFaultClassifier {

    /** Exact case. Not a regex, not a normalisation — the same substring test as today. */
    public static final String ACCOUNT_NOT_FOUND_TEXT = "Account not found";

    /** The billing error code that accompanies it. A string, not a status. */
    public static final String ACCOUNT_NOT_FOUND_CODE = "204";

    private final MeterRegistry meterRegistry;

    public SoapBillingFaultClassifier(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    @MigratedFrom(value = "km:node/N-0084", note = "errorCode == '204' AND faultstring contains the exact text")
    public Classification classify(String faultBody) {
        if (faultBody == null || faultBody.isBlank()) {
            meterRegistry.counter("mule.parity.fault_body_unparseable").increment();
            return Classification.UNPARSEABLE;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(faultBody.getBytes(StandardCharsets.UTF_8)));
            String errorCode = firstTextOf(document, "errorCode");
            String faultString = firstTextOf(document, "faultstring");
            if (ACCOUNT_NOT_FOUND_CODE.equals(errorCode)
                    && faultString != null && faultString.contains(ACCOUNT_NOT_FOUND_TEXT)) {
                meterRegistry.counter("mule.parity.account_not_found").increment();
                return Classification.ACCOUNT_NOT_FOUND;
            }
            return Classification.OTHER_FAULT;
        } catch (Exception notXml) {
            // The legacy assumes XML and would fail here. Counting it is how we find out how often
            // the assumption is wrong (ADR-0011).
            meterRegistry.counter("mule.parity.fault_body_unparseable").increment();
            return Classification.UNPARSEABLE;
        }
    }

    private static String firstTextOf(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            nodes = document.getElementsByTagName(localName);
        }
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }
}
