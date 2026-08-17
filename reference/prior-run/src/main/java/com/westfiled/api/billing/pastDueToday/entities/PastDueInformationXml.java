package com.westfiled.api.billing.pastDueToday.entities;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

import java.math.BigDecimal;

@XmlAccessorType(XmlAccessType.FIELD)
public class PastDueInformationXml {
    private String policyNumber;
    private BigDecimal minimumAmountWithOutFees;
    private String dueDate;

    public String getPolicyNumber() {
        return policyNumber;
    }

    public BigDecimal getMinimumAmountWithOutFees() {
        return minimumAmountWithOutFees;
    }

    public String getDueDate() {
        return dueDate;
    }
}
