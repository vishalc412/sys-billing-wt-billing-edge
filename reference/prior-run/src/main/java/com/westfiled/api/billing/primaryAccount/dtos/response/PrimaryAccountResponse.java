package com.westfiled.api.billing.primaryAccount.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrimaryAccountResponse {
    private BigDecimal accountBalance;
    private String accountNumber;
    private BillToContact billTocontact;
    private String billingSystemCode;
    private String billType;
    private BigDecimal dueAmount;
    private String dueDate;
    private String dateBilled;
    private BigDecimal lastPaymentAmount;
    private String lastPaymentDate;
    private Boolean eft;
    private BigDecimal pastDueAmount;
    private String pastDueDate;
    @Builder.Default
    private List<PolicySummary> policies = new ArrayList<>();
}
