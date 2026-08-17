package com.westfiled.api.billing.primaryAccount.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscrowTransactionsResponse {
    private String policyNumber;
    private String policyVersion;
    @Builder.Default
    private List<TransactionResponse> escrowTransactions = new ArrayList<>();
}
