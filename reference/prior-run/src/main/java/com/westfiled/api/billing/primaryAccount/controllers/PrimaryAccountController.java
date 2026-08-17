package com.westfiled.api.billing.primaryAccount.controllers;

import com.westfiled.api.billing.primaryAccount.dtos.response.PrimaryAccountResponse;
import com.westfiled.api.billing.primaryAccount.dtos.response.TransactionResponse;
import com.westfiled.api.billing.primaryAccount.services.PrimaryAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET /primaryAccount and GET /primaryAccount/transactions
 * (primaryAccount-implementation.xml, primaryAccountTransactions-implementation.xml).
 */
@Slf4j
@RestController
@Validated
public class PrimaryAccountController {

    private final PrimaryAccountService primaryAccountService;

    public PrimaryAccountController(PrimaryAccountService primaryAccountService) {
        this.primaryAccountService = primaryAccountService;
    }

    @GetMapping("/primaryAccount")
    @Operation(summary = "Get primary account", description = "Retrieves billing summary for a primary account from BillingService")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Primary account found"),
            @ApiResponse(responseCode = "204", description = "No primary account found for the given billingAccountNumber"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "502", description = "BillingService call failed")
    })
    public ResponseEntity<PrimaryAccountResponse> getPrimaryAccount(
            @RequestParam @NotBlank @Size(min = 10, max = 10) String billingAccountNumber,
            @RequestParam(required = false) String policyNumber,
            @RequestParam(required = false) String policyVersion) {
        log.info("Received request to get primary account for billingAccountNumber: {}", billingAccountNumber);
        PrimaryAccountResponse response = primaryAccountService.getPrimaryAccount(billingAccountNumber, policyNumber, policyVersion);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/primaryAccount/transactions")
    @Operation(summary = "Get primary account transactions", description = "Retrieves the transaction history for a primary account from BillingService")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transactions returned (possibly empty)"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "502", description = "BillingService call failed")
    })
    public ResponseEntity<List<TransactionResponse>> getTransactions(
            @RequestParam @NotBlank @Size(min = 10, max = 10) String billingAccountNumber,
            @RequestParam(required = false) String policyNumber,
            @RequestParam(required = false) String policyVersion) {
        log.info("Received request to get primary account transactions for billingAccountNumber: {}", billingAccountNumber);
        List<TransactionResponse> response = primaryAccountService.getTransactions(billingAccountNumber, policyNumber, policyVersion);
        return ResponseEntity.ok(response);
    }
}
