package com.westfiled.api.billing.primaryAccount.entities;

import jakarta.xml.bind.annotation.XmlAccessOrder;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorOrder;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

/** Mirrors the PrimaryAccountRequest SOAP body built in callBillingService-primaryAccount. */
@XmlRootElement(name = "PrimaryAccountRequest", namespace = "http://www.westfieldgrp.com/billing")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlAccessorOrder(XmlAccessOrder.UNDEFINED)
public class PrimaryAccountRequest {

    private String accountNumber;
    private String policyNumber;
    private String policyVersion;

    public PrimaryAccountRequest() {
    }

    public PrimaryAccountRequest(String accountNumber, String policyNumber, String policyVersion) {
        this.accountNumber = accountNumber;
        this.policyNumber = policyNumber == null ? "" : policyNumber;
        this.policyVersion = policyVersion == null ? "" : policyVersion;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }
}
