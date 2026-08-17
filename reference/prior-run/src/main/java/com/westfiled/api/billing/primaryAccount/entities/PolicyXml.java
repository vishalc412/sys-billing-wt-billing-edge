package com.westfiled.api.billing.primaryAccount.entities;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps a single {@code <policies>} element. The Mule source notes the BillingService XML repeats
 * {@code <policies>} as sibling elements under {@code <primaryAccount>} rather than wrapping them
 * (hence its "duplicateKeyAsArray" DataWeave workaround); JAXB collects repeated same-named
 * elements into a List natively, so {@link PrimaryAccountXml#getPolicies()} needs no such
 * workaround. The same applies to the nested {@code <policyTerm>} elements here.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class PolicyXml {
    private String policyNumber;
    @XmlElement(name = "policyTerm")
    private List<PolicyTermXml> policyTerm = new ArrayList<>();

    public String getPolicyNumber() {
        return policyNumber;
    }

    public List<PolicyTermXml> getPolicyTerm() {
        return policyTerm;
    }
}
