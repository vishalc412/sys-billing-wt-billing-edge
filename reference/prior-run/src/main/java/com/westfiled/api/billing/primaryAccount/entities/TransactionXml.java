package com.westfiled.api.billing.primaryAccount.entities;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

import java.math.BigDecimal;

@XmlAccessorType(XmlAccessType.FIELD)
public class TransactionXml {
    private BigDecimal amountDue;
    private String description;
    private String dueDate;
    private String processingDate;

    public BigDecimal getAmountDue() {
        return amountDue;
    }

    public String getDescription() {
        return description;
    }

    public String getDueDate() {
        return dueDate;
    }

    public String getProcessingDate() {
        return processingDate;
    }
}
