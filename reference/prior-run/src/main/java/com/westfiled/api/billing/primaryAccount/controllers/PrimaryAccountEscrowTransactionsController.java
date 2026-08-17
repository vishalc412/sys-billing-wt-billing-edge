package com.westfiled.api.billing.primaryAccount.controllers;

import com.westfiled.api.billing.primaryAccount.dtos.response.EscrowTransactionsResponse;
import com.westfiled.api.billing.primaryAccount.services.PrimaryAccountEscrowTransactionsService;
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

/** GET /primaryAccount/policy/escrow/transactions (escrowTransactions-implementation.xml). */
@Slf4j
@RestController
@Validated
public class PrimaryAccountEscrowTransactionsController {

    private final PrimaryAccountEscrowTransactionsService escrowTransactionsService;

    public PrimaryAccountEscrowTransactionsController(PrimaryAccountEscrowTransactionsService escrowTransactionsService) {
        this.escrowTransactionsService = escrowTransactionsService;
    }

    @GetMapping("/primaryAccount/policy/escrow/transactions")
    @Operation(summary = "Get escrow transactions", description = "Retrieves escrow transactions for a policy from BillingService")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Escrow transactions found"),
            @ApiResponse(responseCode = "204", description = "No escrow transactions found"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "502", description = "BillingService call failed")
    })
    public ResponseEntity<EscrowTransactionsResponse> getEscrowTransactions(
            @RequestParam @NotBlank @Size(min = 7, max = 7) String policyNumber,
            @RequestParam @NotBlank @Size(min = 10, max = 10) String billingAccountNumber,
            @RequestParam(required = false) String policyVersion) {
        log.info("Received request to get escrow transactions for policyNumber: {}", policyNumber);
        EscrowTransactionsResponse response = escrowTransactionsService.getEscrowTransactions(billingAccountNumber, policyNumber, policyVersion);
        return ResponseEntity.ok(response);
    }
}
