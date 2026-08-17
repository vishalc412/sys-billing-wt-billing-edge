package com.westfield.api.billing.edge.adapter.out.flags;

import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.platform.observability.MigratedFrom;
import com.westfield.api.billing.platform.spi.LegacyBehaviourFlags;
import org.springframework.stereotype.Component;

/**
 * The concrete {@link LegacyBehaviourFlags} the whole deployable reads (ADR-0001: {@code billing-edge}
 * owns the platform-SPI implementations that the feature modules call).
 *
 * <p>Every switch here points at the ADR that owns it. None of them has a "sensible" default chosen by
 * an engineer: each default is the one an ADR wrote down, which is why this class is a thin projection
 * of configuration rather than a place where defaults live. A flag whose default drifts here would
 * silently overturn a decision.
 */
@Component
@MigratedFrom(value = "km:node/N-0002",
        note = "the environment-selected behaviour switches; ADR-0037, ADR-0038, ADR-0007")
public class ConfiguredLegacyBehaviourFlags implements LegacyBehaviourFlags {

    private final BillingEdgeProperties properties;

    public ConfiguredLegacyBehaviourFlags(BillingEdgeProperties properties) {
        this.properties = properties;
    }

    /** ADR-0037. Enforce in every profile including production; log-only needs a named human owner. */
    @Override
    @MigratedFrom(value = "km:node/N-0035", note = "ADR-0037 enforcement switch")
    public boolean enforceAgencyEntitlement() {
        return properties.getSecurity().getAgencyEntitlement()
                == BillingEdgeProperties.Security.Mode.ENFORCE;
    }

    /** ADR-0038. False in the production profile; the legacy hardcodes it on everywhere. */
    @Override
    @MigratedFrom(value = "km:node/N-0031", note = "ADR-0038 console gating")
    public boolean consoleEnabled() {
        return properties.getConsole().isEnabled();
    }

    /** ADR-0007. Zero means mint per request, which is the cutover default under either legacy behaviour. */
    @Override
    @MigratedFrom(value = "km:node/N-0071", note = "ADR-0007 TTL; zero at cutover")
    public long samlCacheTtlSeconds() {
        return properties.getSaml().getCacheTtlSeconds();
    }
}
