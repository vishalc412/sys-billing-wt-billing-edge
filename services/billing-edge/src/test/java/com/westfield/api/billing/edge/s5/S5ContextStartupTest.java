package com.westfield.api.billing.edge.s5;

import com.westfield.api.billing.edge.SysBillingApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DEF-0100 — the deployable does not start.
 *
 * <p>No test in the S4 suite boots a Spring application context. Every one of the 206 tests the build
 * report counts is a plain JUnit test over a hand-constructed object or a hand-assembled filter chain.
 * The consequence is that the assembly itself — which beans exist, whether they can be constructed,
 * whether the filters are registered — has never been executed, and the build is green anyway.
 *
 * <p>This test starts the real {@link SysBillingApplication} with a valid profile and records what
 * happens. It asserts the FAILURE, deliberately: an S5 test that asserted success and went red would
 * be indistinguishable from a flaky test, while a test that asserts the observed failure is a
 * reproduction that survives until someone fixes the cause.
 *
 * <p>Root cause: {@code ThycoticCredentialProvider} and {@code WsTrustSecurityTokenService} are each
 * annotated {@code @Component} and each declare TWO constructors — a public production one and a
 * package-private test seam taking an injected {@code HttpClient}. Spring's implicit constructor
 * injection only applies when a component declares exactly one constructor; with two and no
 * {@code @Autowired} marker it falls back to the no-argument constructor, which does not exist.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("S5 — DEF-0100: the Spring context does not start")
class S5ContextStartupTest {

    @Test // DEF-0100
    @DisplayName("booting SysBillingApplication with the local profile fails on thycoticCredentialProvider")
    void theApplicationContextFailsToStart() {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(SysBillingApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("local")
                .properties("spring.main.banner-mode=off");

        assertThatThrownBy(() -> {
            try (ConfigurableApplicationContext ignored = builder.run()) {
                // unreachable while DEF-0100 stands
            }
        })
                .hasMessageContaining("thycoticCredentialProvider")
                .hasStackTraceContaining("No default constructor found");
    }

    @Test // DEF-0100 — the second occurrence of the same fault
    @DisplayName("the same two-constructor fault is present on WsTrustSecurityTokenService")
    void theSameFaultIsPresentOnTheStsAdapter() {
        // Asserted structurally rather than by boot order, because the container stops at the first
        // failure and would never reach the second bean.
        assertThat(com.westfield.api.billing.edge.adapter.out.sts.WsTrustSecurityTokenService.class
                .getDeclaredConstructors())
                .as("a @Component with two constructors and no @Autowired cannot be instantiated by Spring")
                .hasSize(2);
        assertThat(com.westfield.api.billing.edge.adapter.out.vault.ThycoticCredentialProvider.class
                .getDeclaredConstructors())
                .hasSize(2);
        assertThat(java.util.Arrays.stream(
                        com.westfield.api.billing.edge.adapter.out.sts.WsTrustSecurityTokenService.class
                                .getDeclaredConstructors())
                .anyMatch(c -> c.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class)))
                .as("neither constructor is marked @Autowired, so Spring cannot choose")
                .isFalse();
    }
}
