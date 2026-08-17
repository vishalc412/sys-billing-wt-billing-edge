package com.westfield.api.billing.edge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westfield.api.billing.edge.adapter.in.web.AgencyEntitlementFilter;
import com.westfield.api.billing.edge.adapter.in.web.AuditFunnelFilter;
import com.westfield.api.billing.edge.adapter.in.web.CallerContextFactory;
import com.westfield.api.billing.edge.adapter.in.web.ContractViolationWriter;
import com.westfield.api.billing.edge.adapter.in.web.CorrelationIdFilter;
import com.westfield.api.billing.edge.adapter.in.web.EndpointAuditFilter;
import com.westfield.api.billing.edge.adapter.in.web.FailureResponseWriter;
import com.westfield.api.billing.edge.adapter.in.web.InboundValidationFilter;
import com.westfield.api.billing.edge.application.failure.UnhandledFailurePresenter;
import com.westfield.api.billing.edge.application.port.AuditEventSink;
import com.westfield.api.billing.edge.domain.api.ApiResourceTable;
import com.westfield.api.billing.edge.domain.api.InboundAdmissionRule;
import com.westfield.api.billing.edge.domain.security.AgencyEntitlementRule;
import com.westfield.api.billing.platform.observability.MigratedFrom;
import com.westfield.api.billing.platform.spi.AuditRecordPort;
import com.westfield.api.billing.platform.spi.LegacyBehaviourFlags;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The assembly: which filters exist, and — the part that carries behaviour — <b>in what order</b>.
 *
 * <p>The legacy main flow is a fixed sequence: the API Manager policy decodes the token at the
 * gateway, then {@code setRequestResponse} builds the audit record, then the APIkit router validates
 * and routes, then {@code responseLogFlow} closes the record (N-0022). The order below reproduces
 * that sequence exactly, and each position is load-bearing:
 *
 * <ol>
 *   <li><b>{@link CorrelationIdFilter} (-120)</b> — outermost, so a request rejected by anything at
 *       all still comes back with an id support can search on (N-0023).</li>
 *   <li><b>Spring Security (-100, framework default)</b> — the token is decoded BEFORE the audit
 *       record is built, because the record projects the token's claims (AUD-001-c). This is the
 *       position the API Manager policy occupied.</li>
 *   <li><b>{@link AuditFunnelFilter} (0)</b> — outside the admission check, so that a request
 *       rejected as a contract violation is still audited with the status that rejection returned
 *       (AUD-004-f, ADM-004-a). Putting admission first would be tidier and would silently drop
 *       every rejected request out of the audit trail.</li>
 *   <li><b>{@link InboundValidationFilter} (10)</b> — the APIkit router's validation half, inside the
 *       funnel exactly as the router sits inside the legacy main flow.</li>
 *   <li><b>{@link AgencyEntitlementFilter} (20)</b> — after admission, so a malformed request is a
 *       400 rather than a 403, and before any implementation, so a denial makes no backend call
 *       (ADR-0037).</li>
 *   <li><b>{@link EndpointAuditFilter} (30)</b> — innermost: the per-endpoint record is emitted at
 *       the head of the endpoint flow in the legacy, after routing has chosen it (N-0036).</li>
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BillingEdgeProperties.class)
@MigratedFrom(value = "km:node/N-0022",
        note = "sapi-billing-search-main processor order, expressed as servlet filter order")
public class BillingEdgeConfiguration {

    /** Outermost. Everything downstream can rely on a correlation id existing. */
    public static final int CORRELATION_ID_FILTER_ORDER = -120;
    /** After Spring Security (-100): the audit record projects decoded token claims. */
    public static final int AUDIT_FUNNEL_FILTER_ORDER = 0;
    /** Inside the funnel: a contract violation is audited like any other outcome. */
    public static final int INBOUND_VALIDATION_FILTER_ORDER = 10;
    public static final int AGENCY_ENTITLEMENT_FILTER_ORDER = 20;
    public static final int ENDPOINT_AUDIT_FILTER_ORDER = 30;

    /**
     * One clock for the whole application. Injected rather than called statically so that the two
     * independent time readings on the audit record (N-0051 ec 5) and the {@code /info} timestamp are
     * testable without sleeping.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ApiResourceTable apiResourceTable() {
        return new ApiResourceTable();
    }

    @Bean
    public InboundAdmissionRule inboundAdmissionRule(ApiResourceTable table) {
        return new InboundAdmissionRule(table);
    }

    /**
     * ADR-0037. The exemption list is read here and nowhere else; {@link StartupConfigurationValidator}
     * has already refused to start if any entry lacks an owner or an expiry.
     */
    @Bean
    public AgencyEntitlementRule agencyEntitlementRule(BillingEdgeProperties properties) {
        Set<String> exempt = new LinkedHashSet<>();
        for (BillingEdgeProperties.Security.ExemptClient client
                : properties.getSecurity().getAgencyEntitlementExemptClients()) {
            if (client.getClientId() != null) {
                exempt.add(client.getClientId());
            }
        }
        return new AgencyEntitlementRule(exempt);
    }

    @Bean
    public CallerContextFactory callerContextFactory() {
        return new CallerContextFactory();
    }

    @Bean
    public ContractViolationWriter contractViolationWriter(ObjectMapper objectMapper) {
        return new ContractViolationWriter(objectMapper);
    }

    @Bean
    public FailureResponseWriter failureResponseWriter(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        return new FailureResponseWriter(objectMapper, meterRegistry);
    }

    @Bean
    public StartupConfigurationValidator startupConfigurationValidator(BillingEdgeProperties properties) {
        return new StartupConfigurationValidator(properties);
    }

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter(BillingEdgeProperties properties) {
        FilterRegistrationBean<CorrelationIdFilter> registration =
                new FilterRegistrationBean<>(new CorrelationIdFilter(properties));
        registration.setOrder(CORRELATION_ID_FILTER_ORDER);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AuditFunnelFilter> auditFunnelFilter(BillingEdgeProperties properties,
                                                                       AuditRecordPort auditRecord,
                                                                       CallerContextFactory callerContextFactory,
                                                                       UnhandledFailurePresenter presenter,
                                                                       FailureResponseWriter failureWriter,
                                                                       MeterRegistry meterRegistry) {
        FilterRegistrationBean<AuditFunnelFilter> registration = new FilterRegistrationBean<>(
                new AuditFunnelFilter(properties, auditRecord, callerContextFactory, presenter,
                        failureWriter, meterRegistry));
        registration.setOrder(AUDIT_FUNNEL_FILTER_ORDER);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<InboundValidationFilter> inboundValidationFilter(
            InboundAdmissionRule admissionRule,
            ContractViolationWriter violationWriter,
            BillingEdgeProperties properties) {
        FilterRegistrationBean<InboundValidationFilter> registration = new FilterRegistrationBean<>(
                new InboundValidationFilter(admissionRule, violationWriter, properties));
        registration.setOrder(INBOUND_VALIDATION_FILTER_ORDER);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AgencyEntitlementFilter> agencyEntitlementFilter(
            AgencyEntitlementRule rule,
            LegacyBehaviourFlags flags,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        FilterRegistrationBean<AgencyEntitlementFilter> registration = new FilterRegistrationBean<>(
                new AgencyEntitlementFilter(rule, flags, objectMapper, meterRegistry));
        registration.setOrder(AGENCY_ENTITLEMENT_FILTER_ORDER);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<EndpointAuditFilter> endpointAuditFilter(ApiResourceTable table,
                                                                           AuditEventSink sink,
                                                                           BillingEdgeProperties properties) {
        FilterRegistrationBean<EndpointAuditFilter> registration =
                new FilterRegistrationBean<>(new EndpointAuditFilter(table, sink, properties));
        registration.setOrder(ENDPOINT_AUDIT_FILTER_ORDER);
        return registration;
    }
}
