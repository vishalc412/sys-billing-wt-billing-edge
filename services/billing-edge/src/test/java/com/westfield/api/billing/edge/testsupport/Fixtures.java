package com.westfield.api.billing.edge.testsupport;

import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.platform.spi.CallerContext;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Shared test data. Kept deliberately explicit rather than clever: every fixture below encodes a
 * condition named in the knowledge map, and a reader should be able to see which one without
 * following a builder chain.
 */
public final class Fixtures {

    private Fixtures() {
    }

    /** A fully populated configuration that passes {@code StartupConfigurationValidator}. */
    public static BillingEdgeProperties validProperties() {
        BillingEdgeProperties properties = new BillingEdgeProperties();
        properties.getApi().setId("15653875");
        properties.getApi().setName("sapi-billing");
        properties.getApi().setVersion("v1");
        properties.getApi().setBasePath("/");
        properties.setPasmIdBilling("1988");
        properties.getSaml().setAudience("https://test.westfieldgrp.com/billing");
        properties.getBackend().getEsb().setHost("https://tst2esb.westfieldgrp.com");
        properties.getBackend().getWas().setHost("https://was.westfieldgrp.com");
        properties.getBackend().getSts().setHost("https://idp.westfieldgrp.com/sts");
        properties.getBackend().getVault().setHost("https://vault.westfieldgrp.com");
        properties.getTruststore().setLocation("classpath:truststore/test.jks");
        properties.getTruststore().setPassword("a-resolved-per-environment-password");
        properties.getTruststore().setClientCertificate(false);
        return properties;
    }

    public static BillingEdgeProperties.Security.ExemptClient exemption(String clientId,
                                                                       String owner,
                                                                       LocalDate expires) {
        BillingEdgeProperties.Security.ExemptClient exemption =
                new BillingEdgeProperties.Security.ExemptClient();
        exemption.setClientId(clientId);
        exemption.setOwner(owner);
        exemption.setExpires(expires);
        return exemption;
    }

    /** A caller with every optional identity field supplied and no actor. */
    public static CallerContext fullyPopulatedCaller() {
        return new CallerContext("u123456", "adjuster@westfieldgrp.com", "client-abc",
                CallerContext.EMPTY, CallerContext.EMPTY, false, List.of("A0421", "A0999"));
    }

    /** A caller whose token supplied none of the optional fields (AUD-001-d). */
    public static CallerContext sparseCaller() {
        return new CallerContext(CallerContext.EMPTY, CallerContext.EMPTY, "client-abc",
                CallerContext.EMPTY, CallerContext.EMPTY, false, List.of());
    }

    /** The nested-{@code act} actor representation (AUD-003-a). */
    public static CallerContext nestedActorCaller(String actorSubject, String actorEmail) {
        return new CallerContext("u123456", "adjuster@westfieldgrp.com", "client-abc",
                actorSubject, actorEmail, true, List.of("A0421"));
    }

    /** The flat {@code actSub}/{@code actEmail} actor representation (AUD-003-b). */
    public static CallerContext flatActorCaller(String actorSubject, String actorEmail) {
        return new CallerContext("u123456", "adjuster@westfieldgrp.com", "client-abc",
                actorSubject, actorEmail, false, List.of("A0421"));
    }

    /** A token carrying the standard identity claims and nothing else. */
    public static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("header.payload.signature")
                .header("alg", "RS256")
                .header("typ", "JWT");
        claims.forEach(builder::claim);
        if (!claims.containsKey("sub")) {
            // Jwt requires at least one claim; every real token carries a subject.
            builder.claim("sub", "u123456");
        }
        return builder.build();
    }
}
