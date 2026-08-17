package com.westfiled.api.billing.pastDueToday.entities;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class PastDueXml {
    private PastDueInformationXml pastDueInformation;
    private BillToContactXml billToContact;

    public PastDueInformationXml getPastDueInformation() {
        return pastDueInformation;
    }

    public BillToContactXml getBillToContact() {
        return billToContact;
    }
}
