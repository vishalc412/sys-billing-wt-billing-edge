package com.westfiled.api.billing.primaryAccount.entities;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class PrimaryAccountXml {
    private BigDecimal accountBalance;
    private String accountNumber;
    private BillToContactXml billTocontact;
    private String billingSystemCode;
    private String billType;
    private BigDecimal currentAmountDue;
    private String currentAmountDueDate;
    private String dateBilled;
    private BigDecimal lastPaymentReceived;
    private String lastPaymentReceivedDate;
    private Boolean eftEstablished;
    private BigDecimal pastDueAmount;
    private String pastDueDate;
    @XmlElement(name = "policies")
    private List<PolicyXml> policies = new ArrayList<>();
    @XmlElement(name = "transactions")
    private List<TransactionXml> transactions = new ArrayList<>();

    public BigDecimal getAccountBalance() {
        return accountBalance;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public BillToContactXml getBillTocontact() {
        return billTocontact;
    }

    public String getBillingSystemCode() {
        return billingSystemCode;
    }

    public String getBillType() {
        return billType;
    }

    public BigDecimal getCurrentAmountDue() {
        return currentAmountDue;
    }

    public String getCurrentAmountDueDate() {
        return currentAmountDueDate;
    }

    public String getDateBilled() {
        return dateBilled;
    }

    public BigDecimal getLastPaymentReceived() {
        return lastPaymentReceived;
    }

    public String getLastPaymentReceivedDate() {
        return lastPaymentReceivedDate;
    }

    public Boolean getEftEstablished() {
        return eftEstablished;
    }

    public BigDecimal getPastDueAmount() {
        return pastDueAmount;
    }

    public String getPastDueDate() {
        return pastDueDate;
    }

    public List<PolicyXml> getPolicies() {
        return policies;
    }

    public List<TransactionXml> getTransactions() {
        return transactions;
    }
}
