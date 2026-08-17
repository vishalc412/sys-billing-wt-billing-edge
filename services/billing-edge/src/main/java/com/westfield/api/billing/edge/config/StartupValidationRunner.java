package com.westfield.api.billing.edge.config;

import com.westfield.api.billing.edge.adapter.out.build.BuildMetadataAdapter;
import com.westfield.api.billing.platform.observability.MigratedFrom;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs the fail-fast checks while the context is still starting, so a misconfigured instance never
 * serves a request (ADR-0018, CFG-001, CFG-002, INF-001-c).
 *
 * <p>{@link InitializingBean} rather than an {@code ApplicationRunner}: a runner executes after the
 * context is refreshed and the listener is already accepting connections, which would let a
 * misconfigured instance answer requests for the moment before it died. Failing during
 * initialisation is what makes "refuses to start" true rather than approximately true.
 */
@Component
@MigratedFrom(value = "km:node/N-0002",
        note = "mule.env resolution moved to startup; ADR-0018 refuses to start rather than defaulting")
public class StartupValidationRunner implements InitializingBean {

    private final StartupConfigurationValidator validator;
    private final BuildMetadataAdapter buildMetadata;
    private final Environment environment;

    public StartupValidationRunner(StartupConfigurationValidator validator,
                                   BuildMetadataAdapter buildMetadata,
                                   Environment environment) {
        this.validator = validator;
        this.buildMetadata = buildMetadata;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        validator.validate(List.of(environment.getActiveProfiles()));
        // An unsubstituted build-tool token must fail the build/boot rather than being served by
        // /info, which is the silent failure mode the legacy has today (N-0011 ec, ADR-0018).
        buildMetadata.validateNoUnsubstitutedTokens();
    }
}
