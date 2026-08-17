package com.westfiled.api.billing.configs;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Logs circuit breaker state transitions for the billingService / samlToken instances (thresholds
 * themselves are configured declaratively under resilience4j.* in application.yaml).
 */
@Slf4j
@Configuration
public class Resilience4jConfig {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public Resilience4jConfig(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @PostConstruct
    public void registerEventListeners() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(this::registerListener);
        circuitBreakerRegistry.getEventPublisher()
                .onEntryAdded(event -> registerListener(event.getAddedEntry()));
    }

    private void registerListener(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher().onStateTransition(event ->
                log.warn("Circuit breaker '{}' transitioned {} -> {}",
                        circuitBreaker.getName(),
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()));
    }
}
