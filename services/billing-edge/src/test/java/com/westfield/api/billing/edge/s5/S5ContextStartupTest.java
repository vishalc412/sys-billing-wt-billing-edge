package com.westfield.api.billing.edge.s5;

import com.westfield.api.billing.edge.SysBillingApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DEF-0100 — the deployable now starts.
 *
 * <p>Previously {@code ThycoticCredentialProvider} and {@code WsTrustSecurityTokenService} were each
 * annotated {@code @Component} and declared two constructors with no {@code @Autowired}, so Spring's
 * implicit constructor injection could not choose and the context died before anything else could be
 * observed. The production constructors are now marked {@code @Autowired}; the context boots unaided
 * and the {@code S5TestSupport} bean-overriding work-around has been removed.
 *
 * <p>This test boots the real {@link SysBillingApplication} with the local profile in a MOCK servlet
 * web context (no real socket) and asserts the boot SUCCEEDS. {@code S5TestSupport} is pulled in only
 * for its stub {@code JwtDecoder} — every profile configures {@code issuer-uri}, so without the stub
 * the context would attempt OIDC discovery against an unreachable IdP at bean creation. That is a
 * test-environment substitution, not a DEF-0100 work-around. A MOCK web context (not NONE) is required
 * because {@code SecurityConfiguration#securityFilterChain} injects {@code HttpSecurity}, which the
 * web-security auto-configuration only provides when a servlet web application context is present.
 */
@SpringBootTest(
        classes = {SysBillingApplication.class, S5TestSupport.class},
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.banner-mode=off")
@org.springframework.test.context.ActiveProfiles("local")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("S5 — DEF-0100: the Spring context starts")
class S5ContextStartupTest {

    @Autowired
    ConfigurableApplicationContext context;

    @Test // DEF-0100
    @DisplayName("booting SysBillingApplication with the local profile starts the context")
    void theApplicationContextStartsSuccessfully() {
        assertThat(context.isActive())
                .as("the context starts once the @Component adapters are constructor-injectable")
                .isTrue();
        // The two adapters that previously failed Spring instantiation are now present as beans.
        assertThat(context.containsBean("thycoticCredentialProvider")).isTrue();
        assertThat(context.containsBean("wsTrustSecurityTokenService")).isTrue();
    }

    @Test // DEF-0100 — the second occurrence of the same fault, now corrected
    @DisplayName("each @Component adapter has @Autowired on its production constructor")
    void theSameFaultIsCorrectedOnBothAdapters() {
        assertProductionConstructorAutowired(
                com.westfield.api.billing.edge.adapter.out.sts.WsTrustSecurityTokenService.class);
        assertProductionConstructorAutowired(
                com.westfield.api.billing.edge.adapter.out.vault.ThycoticCredentialProvider.class);
    }

    private static void assertProductionConstructorAutowired(Class<?> component) {
        Constructor<?>[] constructors = component.getDeclaredConstructors();
        assertThat(constructors)
                .as("%s declares the test seam and the production constructor", component.getSimpleName())
                .hasSize(2);
        long autowired = Arrays.stream(constructors)
                .filter(c -> c.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class))
                .count();
        assertThat(autowired)
                .as("exactly one constructor is @Autowired so Spring can choose the production one")
                .isEqualTo(1);
    }
}