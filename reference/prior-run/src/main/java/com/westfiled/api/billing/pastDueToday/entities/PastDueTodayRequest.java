package com.westfiled.api.billing.pastDueToday.entities;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

/** Mirrors the PastDueTodayRequest SOAP body built in get-past-due-today-flow. */
@XmlRootElement(name = "PastDueTodayRequest", namespace = "http://www.westfieldgrp.com/billing")
@XmlAccessorType(XmlAccessType.FIELD)
public class PastDueTodayRequest {

    private String agencyCode;
    private String subAgenciesIncluded;

    public PastDueTodayRequest() {
    }

    public PastDueTodayRequest(String agencyCode, String subAgenciesIncluded) {
        this.agencyCode = agencyCode;
        this.subAgenciesIncluded = subAgenciesIncluded;
    }
}
