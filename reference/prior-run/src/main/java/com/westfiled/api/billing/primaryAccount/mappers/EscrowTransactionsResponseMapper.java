package com.westfiled.api.billing.primaryAccount.mappers;

import com.westfiled.api.billing.primaryAccount.dtos.response.EscrowTransactionsResponse;
import com.westfiled.api.billing.primaryAccount.entities.PolicyTermXml;
import com.westfiled.api.billing.primaryAccount.entities.PolicyXml;
import com.westfiled.api.billing.primaryAccount.entities.PrimaryAccountXml;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Replaces the "set payload" DataWeave transform in mapResponse_escrowTransactions
 * (escrowTransactions-implementation.xml). The response's {@code policyNumber} field is populated
 * from the BillingService {@code accountNumber} field, not the policy's own number — that is what
 * the original DataWeave did ({@code "policyNumber": billingData.accountNumber}); kept as-is here
 * rather than "corrected", but worth confirming with the business — see
 * generated/sys/report.md.
 */
@Component
public class EscrowTransactionsResponseMapper {

    private final TransactionResponseMapper transactionResponseMapper;

    public EscrowTransactionsResponseMapper(TransactionResponseMapper transactionResponseMapper) {
        this.transactionResponseMapper = transactionResponseMapper;
    }

    public EscrowTransactionsResponse toResponse(PrimaryAccountXml billingData, String requestedPolicyVersion) {
        List<PolicyXml> policies = billingData.getPolicies();
        List<PolicyTermXml> terms = policies.isEmpty() ? List.of() : policies.get(0).getPolicyTerm();

        String targetVersion = requestedPolicyVersion != null
                ? requestedPolicyVersion
                : terms.isEmpty() ? null : terms.get(terms.size() - 1).getVersion();

        List<PolicyTermXml> matchingTerms = terms.stream()
                .filter(term -> Objects.equals(term.getVersion(), targetVersion))
                .toList();

        List<com.westfiled.api.billing.primaryAccount.entities.TransactionXml> transactions = matchingTerms.stream()
                .flatMap(term -> term.getEscrowAccount() == null
                        ? Stream.<com.westfiled.api.billing.primaryAccount.entities.TransactionXml>empty()
                        : term.getEscrowAccount().getTransactions().stream())
                .toList();

        return EscrowTransactionsResponse.builder()
                .policyNumber(billingData.getAccountNumber())
                .policyVersion(targetVersion)
                .escrowTransactions(transactionResponseMapper.toResponseList(transactions))
                .build();
    }
}
