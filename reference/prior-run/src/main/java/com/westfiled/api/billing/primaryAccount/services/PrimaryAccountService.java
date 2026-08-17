package com.westfiled.api.billing.primaryAccount.services;

import com.westfiled.api.billing.primaryAccount.dtos.response.PrimaryAccountResponse;
import com.westfiled.api.billing.primaryAccount.dtos.response.TransactionResponse;
import com.westfiled.api.billing.primaryAccount.entities.PrimaryAccountXml;
import com.westfiled.api.billing.primaryAccount.mappers.PrimaryAccountResponseMapper;
import com.westfiled.api.billing.primaryAccount.mappers.TransactionResponseMapper;
import com.westfiled.api.billing.soap.exceptions.NoContentFoundException;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Backs GET /primaryAccount and GET /primaryAccount/transactions (primaryAccount-implementation.xml,
 * primaryAccountTransactions-implementation.xml). The two flows disagree on what an empty result
 * means: primaryAccount returns 204 No Content, transactions returns 200 with an empty array —
 * both are preserved here (see {@link com.westfiled.api.billing.primaryAccount.controllers.PrimaryAccountController}).
 */
@Slf4j
@Service
@Observed(name = "billing.primaryaccount.service", contextualName = "Primary Account Service")
public class PrimaryAccountService {

    private final PrimaryAccountBillingClient billingClient;
    private final PrimaryAccountResponseMapper primaryAccountResponseMapper;
    private final TransactionResponseMapper transactionResponseMapper;

    public PrimaryAccountService(PrimaryAccountBillingClient billingClient,
                                  PrimaryAccountResponseMapper primaryAccountResponseMapper,
                                  TransactionResponseMapper transactionResponseMapper) {
        this.billingClient = billingClient;
        this.primaryAccountResponseMapper = primaryAccountResponseMapper;
        this.transactionResponseMapper = transactionResponseMapper;
    }

    public PrimaryAccountResponse getPrimaryAccount(String billingAccountNumber, String policyNumber, String policyVersion) {
        log.info("Fetching primary account for billingAccountNumber: {}", billingAccountNumber);
        Optional<PrimaryAccountXml> billingData = billingClient.fetch(billingAccountNumber, policyNumber, policyVersion);
        return billingData.map(primaryAccountResponseMapper::toResponse)
                .orElseThrow(() -> new NoContentFoundException("No primary account found for billingAccountNumber " + billingAccountNumber));
    }

    public List<TransactionResponse> getTransactions(String billingAccountNumber, String policyNumber, String policyVersion) {
        log.info("Fetching primary account transactions for billingAccountNumber: {}", billingAccountNumber);
        Optional<PrimaryAccountXml> billingData = billingClient.fetch(billingAccountNumber, policyNumber, policyVersion);
        return billingData.map(xml -> transactionResponseMapper.toResponseList(xml.getTransactions()))
                .orElse(List.of());
    }
}
