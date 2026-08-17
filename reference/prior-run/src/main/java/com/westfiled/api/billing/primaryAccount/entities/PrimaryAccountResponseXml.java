package com.westfiled.api.billing.primaryAccount.entities;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

/** Unmarshalled from the {@code <PrimaryAccountResponse>} fragment extracted out of the SOAP body. */
@XmlRootElement(name = "PrimaryAccountResponse", namespace = "http://www.westfieldgrp.com/billing")
@XmlAccessorType(XmlAccessType.FIELD)
public class PrimaryAccountResponseXml {
    private PrimaryAccountXml primaryAccount;

    public PrimaryAccountXml getPrimaryAccount() {
        return primaryAccount;
    }
}
