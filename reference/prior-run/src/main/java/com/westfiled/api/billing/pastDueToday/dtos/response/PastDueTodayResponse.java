package com.westfiled.api.billing.pastDueToday.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PastDueTodayResponse {
    private String policyNumber;
    private NamedInsured namedInsured;
    private BigDecimal overDueAmount;
    private String dueDate;
}
