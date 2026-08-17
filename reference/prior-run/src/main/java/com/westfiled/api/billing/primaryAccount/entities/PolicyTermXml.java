package com.westfiled.api.billing.primaryAccount.entities;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class PolicyTermXml {
    private String version;
    private String status;
    private EscrowAccountXml escrowAccount;

    public String getVersion() {
        return version;
    }

    public String getStatus() {
        return status;
    }

    public EscrowAccountXml getEscrowAccount() {
        return escrowAccount;
    }
}
