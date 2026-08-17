package com.westfiled.api.billing.primaryAccount.services;

import com.westfiled.api.billing.primaryAccount.dtos.response.EscrowTransactionsResponse;
import com.westfiled.api.billing.primaryAccount.mappers.EscrowTransactionsResponseMapper;
import com.westfiled.api.billing.soap.exceptions.NoContentFoundException;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Backs GET /primaryAccount/policy/escrow/transactions (escrowTransactions-implementation.xml).
 *
 * <p>The Mule flow's "no PrimaryAccountResponse present" branch set the payload to a bare JSON
 * array ({@code []}) with HTTP 200, which conflicts with the RAML-documented object response shape
 * ({policyNumber, policyVersion, escrowTransactions}) — almost certainly copy-pasted from the
 * sibling transactions flow rather than intentional. That case is unified here with the fault-based
 * "Account not found" case (which the same Mule flow already maps to 204) instead of reproducing
 * the shape mismatch. See generated/sys/report.md.
 */
@Slf4j
@Service
@Observed(name = "billing.primaryaccountescrowtransactions.service", contextualName = "Primary Account Escrow Transactions Service")
public class PrimaryAccountEscrowTransactionsService {

    private final PrimaryAccountBillingClient billingClient;
    private final EscrowTransactionsResponseMapper escrowTransactionsResponseMapper;

    public PrimaryAccountEscrowTransactionsService(PrimaryAccountBillingClient billingClient,
                                                     EscrowTransactionsResponseMapper escrowTransactionsResponseMapper) {
        this.billingClient = billingClient;
        this.escrowTransactionsResponseMapper = escrowTransactionsResponseMapper;
    }

    public EscrowTransactionsResponse getEscrowTransactions(String billingAccountNumber, String policyNumber, String policyVersion) {
        log.info("Fetching escrow transactions for billingAccountNumber: {}, policyNumber: {}", billingAccountNumber, policyNumber);
        return billingClient.fetch(billingAccountNumber, policyNumber, policyVersion)
                .map(xml -> escrowTransactionsResponseMapper.toResponse(xml, policyVersion))
                .orElseThrow(() -> new NoContentFoundException(
                        "No escrow transactions found for billingAccountNumber " + billingAccountNumber + ", policyNumber " + policyNumber));
    }
}
