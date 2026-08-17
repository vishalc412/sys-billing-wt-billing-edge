package com.westfield.api.billing.edge.adapter.in.web;

import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.edge.domain.api.ContractViolation;
import com.westfield.api.billing.edge.domain.api.InboundAdmissionRule;
import com.westfield.api.billing.platform.observability.MigratedFrom;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Contract-first admission, in front of every implementation (N-0001).
 *
 * <p>This is the APIkit router's validation half. It runs before anything routes, so a request that
 * violates the contract is rejected before any backend call is attempted — which is the property
 * ADM-002-b and ADM-002-c actually care about, not the status code.
 *
 * <p>It deliberately does NOT route: routing is Spring's, and the modules that implement the eight
 * operations own their own controllers. What lives here is the admission surface the legacy kept in
 * the RAML, because the knowledge map is explicit that those parameter rules are business rules that
 * happen to live in a spec file rather than in a flow.
 *
 * <p>The environment query parameter is enforced on the four resources that declare it and on
 * nothing else. No implementation reads it, in the legacy or here; the deployment environment
 * selects the back ends, and {@code environment=PROD} on a test deployment changes nothing
 * (ADM-002-e). Preserved because relaxing it would accept requests the legacy rejects.
 */
@MigratedFrom(value = "km:node/N-0001", note = "apikit validation ahead of routing; six failure classes")
public class InboundValidationFilter extends OncePerRequestFilter {

    private final InboundAdmissionRule admissionRule;
    private final ContractViolationWriter violationWriter;
    private final BillingEdgeProperties properties;

    public InboundValidationFilter(InboundAdmissionRule admissionRule,
                                   ContractViolationWriter violationWriter,
                                   BillingEdgeProperties properties) {
        this.admissionRule = admissionRule;
        this.violationWriter = violationWriter;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (ConsolePaths.isConsole(request, properties)) {
            chain.doFilter(request, response);
            return;
        }
        Optional<ContractViolation> violation = admissionRule.admit(
                request.getMethod(),
                request.getRequestURI(),
                queryParameters(request),
                request.getHeader("Accept"),
                request.getContentType(),
                request.getHeader("Authorization"));
        if (violation.isPresent()) {
            // Rejected here: no implementation logic runs, and no backend call is attempted.
            violationWriter.write(response, violation.get());
            return;
        }
        chain.doFilter(request, response);
    }

    private static Map<String, List<String>> queryParameters(HttpServletRequest request) {
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        request.getParameterMap().forEach((name, values) -> {
            List<String> supplied = new ArrayList<>(List.of(values));
            parameters.put(name, supplied);
        });
        return parameters;
    }
}
