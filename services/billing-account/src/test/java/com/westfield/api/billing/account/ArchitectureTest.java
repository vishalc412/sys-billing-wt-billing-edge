package com.westfield.api.billing.account;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.westfield.api.billing.platform.testing.ArchitectureRules;

/** Layering is enforced, not suggested (ADR-0001, ADR-0044). */
@AnalyzeClasses(packages = "com.westfield.api.billing.account",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsFrameworkFree = ArchitectureRules.DOMAIN_IS_FRAMEWORK_FREE;

    @ArchTest
    static final ArchRule featureModulesAreIndependent = ArchitectureRules.FEATURE_MODULES_ARE_INDEPENDENT;

    @ArchTest
    static final ArchRule featuresDoNotDependOnEdge = ArchitectureRules.FEATURES_DO_NOT_DEPEND_ON_EDGE;

    @ArchTest
    static final ArchRule noRetryOrTransactions = ArchitectureRules.NO_RETRY_OR_TRANSACTIONS;
}
