package com.westfiled.api.billing.primaryAccount.entities;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

import java.util.ArrayList;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class EscrowAccountXml {
    @XmlElement(name = "transactions")
    private List<TransactionXml> transactions = new ArrayList<>();

    public List<TransactionXml> getTransactions() {
        return transactions;
    }
}
