
package com.westfield.api.billing.edge;

import com.westfield.api.billing.platform.observability.MigratedFrom;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The single deployable assembly (ADR-0001). It composes billing-account and billing-agency; the
 * repository is a modular monolith, so services/ here does NOT mean independently deployable.
 *
 * <p>This class stands in for the legacy application and its HTTP listener configuration (N-0013).
 * Two facts about that listener are carried forward deliberately rather than quietly improved: it
 * binds all interfaces over plain HTTP with TLS terminated upstream, and the RAML advertises HTTPS
 * that the listener itself never speaks. Closing that gap is an infrastructure concern, not an
 * application one — but it is also why ADR-0013 makes the service validate the bearer token itself
 * instead of assuming a gateway did: anything that can reach this port today is unauthenticated.
 *
 * <p>ADR-0018: there is no environment default. The legacy silently ran against development back ends
 * when nothing was set; this application refuses to start without an explicit profile.
 */
@SpringBootApplication(scanBasePackages = "com.westfield.api.billing")
@MigratedFrom(value = "km:node/N-0013",
        note = "the Mule application and its HTTP listener config; 0.0.0.0 over plain HTTP, TLS upstream")
public class SysBillingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SysBillingApplication.class, args);
    }
}
