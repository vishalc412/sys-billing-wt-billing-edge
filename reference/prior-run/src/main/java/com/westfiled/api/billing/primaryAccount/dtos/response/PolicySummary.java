package com.westfiled.api.billing.primaryAccount.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicySummary {
    private String policyNumber;
    private Boolean hasEscrow;
    private String billingStatus;
}
