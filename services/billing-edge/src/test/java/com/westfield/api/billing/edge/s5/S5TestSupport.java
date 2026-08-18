package com.westfield.api.billing.edge.s5;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * S5 (VERIFY) test support. Written by the test-evidence engineer, not by the migration author.
 *
 * <p>The S4 suite exercises every servlet filter through a hand-assembled {@code TestFilterChain} and
 * never boots a Spring context. 85 of this packet's 185 acceptance criteria are declared
 * {@code verifiable_by: integration}. A filter invoked directly with a {@code MockHttpServletRequest}
 * demonstrates that the filter's own code does what its author expected; it demonstrates nothing about
 * whether the filter is REGISTERED, in what ORDER, with what SECURITY chain in front of it, or whether
 * the controller it guards is mapped at all. This support class exists so that those questions can be
 * asked against a running container.
 *
 * <p>The {@link JwtDecoder} is stubbed rather than reached over the network because Spring Boot's
 * issuer-uri decoder performs OIDC discovery at bean-creation time; every profile in this service
 * configures {@code issuer-uri}, so no profile can be booted without either a reachable IdP or this
 * substitution. That fact is itself recorded as an observation in the evidence pack.
 */
@TestConfiguration(proxyBeanMethods = false)
public class S5TestSupport {

    /** Token value -> claims. A test registers a token, then sends it as a bearer credential. */
    public static final Map<String, Map<String, Object>> TOKENS = new ConcurrentHashMap<>();

    /** The token value used when a test only needs "some valid token". */
    public static final String VALID = "s5-valid-token";

    /** A token value the decoder always rejects, standing in for a signature or expiry failure. */
    public static final String INVALID = "s5-invalid-token";

    static {
        TOKENS.put(VALID, defaultClaims());
    }

    public static Map<String, Object> defaultClaims() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "u123456");
        claims.put("email", "adjuster@westfieldgrp.com");
        claims.put("clientId", "client-abc");
        claims.put("agencyCodes", java.util.List.of("A0421", "A0999"));
        return claims;
    }

    /** Registers a token with the given claims and returns its value, for use as a bearer credential. */
    public static String tokenWith(String name, Map<String, Object> claims) {
        TOKENS.put(name, claims);
        return name;
    }

    /**
     * Stub {@link JwtDecoder} so a booted context does not perform OIDC discovery against an
     * unreachable IdP at bean-creation time. Every profile configures {@code issuer-uri}, so without
     * this substitution no profile can boot in a test environment. This is a test-environment
     * substitution, not a DEF-0100 work-around: the two outbound adapters are now instantiated by
     * Spring through their {@code @Autowired} production constructors, so no bean overriding is
     * needed and {@code spring.main.allow-bean-definition-overriding} is no longer set anywhere.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> {
            Map<String, Object> claims = TOKENS.get(token);
            if (claims == null) {
                // BadJwtException, not a bare JwtException. NimbusJwtDecoder raises BadJwtException
                // for a token it cannot validate; JwtAuthenticationProvider maps that to
                // InvalidBearerTokenException (401) and maps a bare JwtException to
                // AuthenticationServiceException, which Spring Security 6 RETHROWS as a 500. A stub
                // that threw the wrong subclass would have produced a fabricated defect.
                throw new org.springframework.security.oauth2.jwt.BadJwtException(
                        "S5 stub decoder rejected the token '" + token + "'");
            }
            Map<String, Object> headers = Map.of("alg", "none", "typ", "JWT");
            return new Jwt(token, Instant.now().minusSeconds(60), Instant.now().plusSeconds(600),
                    headers, claims);
        };
    }
}
