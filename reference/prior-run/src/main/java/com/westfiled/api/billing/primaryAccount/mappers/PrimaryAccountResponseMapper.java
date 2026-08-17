package com.westfiled.api.billing.primaryAccount.mappers;

import com.westfiled.api.billing.primaryAccount.dtos.response.Address;
import com.westfiled.api.billing.primaryAccount.dtos.response.BillToContact;
import com.westfiled.api.billing.primaryAccount.dtos.response.PolicySummary;
import com.westfiled.api.billing.primaryAccount.dtos.response.PrimaryAccountResponse;
import com.westfiled.api.billing.primaryAccount.entities.BillToContactXml;
import com.westfiled.api.billing.primaryAccount.entities.PolicyTermXml;
import com.westfiled.api.billing.primaryAccount.entities.PolicyXml;
import com.westfiled.api.billing.primaryAccount.entities.PrimaryAccountXml;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Replaces the "mapFields" DataWeave transform in primaryAccount-implementation.xml. Mule branched
 * on whether the billingAccountNumber started with "6000" (DuckCreek vs BCMS) purely to work
 * around whether the XML-to-JSON flattening left {@code policyTerm} as an object or an array;
 * since {@link PolicyXml#getPolicyTerm()} is always a List here, both branches collapse to the
 * same computation and no such branch is needed.
 */
@Component
public class PrimaryAccountResponseMapper {

    public PrimaryAccountResponse toResponse(PrimaryAccountXml xml) {
        return PrimaryAccountResponse.builder()
                .accountBalance(round(xml.getAccountBalance()))
                .accountNumber(xml.getAccountNumber())
                .billTocontact(toBillToContact(xml.getBillTocontact()))
                .billingSystemCode(xml.getBillingSystemCode())
                .billType(xml.getBillType())
                .dueAmount(round(xml.getCurrentAmountDue()))
                .dueDate(xml.getCurrentAmountDueDate())
                .dateBilled(xml.getDateBilled())
                .lastPaymentAmount(round(xml.getLastPaymentReceived()))
                .lastPaymentDate(xml.getLastPaymentReceivedDate())
                .eft(xml.getEftEstablished())
                .pastDueAmount(round(xml.getPastDueAmount()))
                .pastDueDate(xml.getPastDueDate())
                .policies(toPolicySummaries(xml.getPolicies()))
                .build();
    }

    private BillToContact toBillToContact(BillToContactXml xml) {
        if (xml == null) {
            return null;
        }
        return BillToContact.builder()
                .name(xml.getName())
                .address(Address.builder()
                        .street(xml.getAddressLine1())
                        .city(xml.getCity())
                        .state(xml.getState())
                        .zip(xml.getZip())
                        .build())
                .build();
    }

    private List<PolicySummary> toPolicySummaries(List<PolicyXml> policies) {
        return policies.stream().map(this::toPolicySummary).toList();
    }

    private PolicySummary toPolicySummary(PolicyXml policy) {
        List<PolicyTermXml> terms = policy.getPolicyTerm();
        boolean hasEscrow = terms.stream().anyMatch(term -> term.getEscrowAccount() != null);
        String billingStatus = terms.isEmpty() ? null : terms.get(terms.size() - 1).getStatus();
        return PolicySummary.builder()
                .policyNumber(policy.getPolicyNumber())
                .hasEscrow(hasEscrow)
                .billingStatus(billingStatus)
                .build();
    }

    static BigDecimal round(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
