package com.westfiled.api.billing.pastDueToday.entities;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Unmarshalled from the {@code <PastDueTodayResponse>} fragment. BillingService repeats
 * {@code <pastDues>} as sibling elements (same "not well-formed" XML shape noted in the primaryAccount
 * flows); JAXB collects them into a List natively.
 */
@XmlRootElement(name = "PastDueTodayResponse", namespace = "http://www.westfieldgrp.com/billing")
@XmlAccessorType(XmlAccessType.FIELD)
public class PastDueTodayResponseXml {
    @XmlElement(name = "pastDues")
    private List<PastDueXml> pastDues = new ArrayList<>();

    public List<PastDueXml> getPastDues() {
        return pastDues;
    }
}
