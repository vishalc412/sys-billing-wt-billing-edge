
package com.westfield.api.billing.platform.testing;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Layering and module-boundary rules from ADR-0001 and ADR-0044, enforced in CI.
 *
 * <p>Layering rules that are only written down are layering suggestions. Each module's test source
 * runs these against its own package.
 *
 * <p>{@code allowEmptyShould(true)} is set deliberately: at scaffold time the modules are empty, and
 * a cross-module rule is empty by construction inside the module it protects. The rules must not
 * fail before any code exists, or the first engineer will delete them.
 */
public final class ArchitectureRules {

    private ArchitectureRules() {
    }

    /** domain holds the rules extracted from DataWeave and must be testable without a framework. */
    public static final ArchRule DOMAIN_IS_FRAMEWORK_FREE = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "jakarta.xml.bind..",
                    "com.fasterxml.jackson..", "..adapter..")
            .allowEmptyShould(true);

    /** No business rule may live in an adapter; adapters translate, they do not decide. */
    public static final ArchRule ADAPTERS_HOLD_NO_RULES = noClasses()
            .that().resideInAPackage("..adapter..")
            .should().dependOnClassesThat().resideInAPackage("..domain.rules..")
            .allowEmptyShould(true);

    /** ADR-0001: the two feature modules never see each other. */
    public static final ArchRule FEATURE_MODULES_ARE_INDEPENDENT = noClasses()
            .that().resideInAPackage("com.westfield.api.billing.account..")
            .should().dependOnClassesThat().resideInAPackage("com.westfield.api.billing.agency..")
            .allowEmptyShould(true);

    /** ADR-0001: no feature module depends on the assembly. */
    public static final ArchRule FEATURES_DO_NOT_DEPEND_ON_EDGE = noClasses()
            .that().resideInAnyPackage("com.westfield.api.billing.account..",
                    "com.westfield.api.billing.agency..")
            .should().dependOnClassesThat().resideInAPackage("com.westfield.api.billing.edge..")
            .allowEmptyShould(true);

    /** ADR-0009 / ADR-0014: nothing retries, and nothing is transactional. */
    public static final ArchRule NO_RETRY_OR_TRANSACTIONS = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                    "io.github.resilience4j.retry..", "org.springframework.retry..",
                    "org.springframework.transaction..")
            .allowEmptyShould(true);
}
