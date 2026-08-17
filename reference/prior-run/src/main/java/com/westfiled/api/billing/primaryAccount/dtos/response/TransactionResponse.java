package com.westfiled.api.billing.primaryAccount.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private BigDecimal amountDue;
    private String description;
    private String dueDate;
    private String processingDate;
}
