package com.westfield.api.billing.edge.domain.api;

import com.westfield.api.billing.platform.observability.MigratedFrom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One resource declared by the API contract, with the admission rules that the legacy RAML carried
 * and the APIkit router enforced before any flow ran (N-0001).
 *
 * <p>The router is contract-first: routing, parameter validation and the required-query-parameter
 * check all come from the declared surface, not from the implementation. That is why this table
 * lives in {@code domain}: it is a business rule that happened to be expressed in a spec file, and
 * the knowledge map says so in as many words — "that length validation is a business rule that lives
 * only in the spec, not in any flow" (N-0001 ec 3).
 *
 * @param method                the only verb the contract declares for this resource; the API is
 *                              read-only, so it is always GET (N-0026)
 * @param template              the resource template, with {@code {name}} placeholders
 * @param requiresEnvironmentParam whether the RAML {@code environment} trait applies. It applies to
 *                              account-billing, policy-billing, pastDueToday and pendingCancelToday
 *                              and to nothing else (N-0045 ec 2), and NO implementation ever reads
 *                              the parameter (N-0034 ec 2, N-0038 ec 2). Preserved: removing the
 *                              requirement would accept requests the legacy rejects.
 * @param emitsEndpointAuditRecord whether this resource emits the per-endpoint audit record. Three
 *                              of the eight do not, and ADR-0017 preserves that gap.
 * @param implemented           whether an implementation stands behind the resource. A declared
 *                              resource with no implementation is 501, never 404 (N-0029).
 */
@MigratedFrom(value = "km:node/N-0001",
        note = "apikit router surface: routing, URI-parameter validation, required query parameters")
public record ApiResource(
        String method,
        String template,
        boolean requiresEnvironmentParam,
        boolean emitsEndpointAuditRecord,
        boolean implemented) {

    /** The environment query parameter the RAML trait makes mandatory but nothing reads. */
    public static final String ENVIRONMENT_PARAM = "environment";

    /**
     * Parameter length rules from the RAML data types (N-0001 ec 3). They apply to a value wherever
     * it appears — path segment or query string — because ADM-002-b/c define them that way and the
     * legacy declares the same data type in both positions.
     */
    public static final Map<String, Integer> EXACT_LENGTH_PARAMETERS = Map.of(
            "billingAccountNumber", 10,
            "policyNumber", 7);

    public boolean matches(String requestMethod, String path) {
        return pathMatches(path) && method.equalsIgnoreCase(requestMethod);
    }

    /** True when the path shape matches, regardless of verb — this is what separates 404 from 405. */
    public boolean pathMatches(String path) {
        List<String> templateSegments = segments(template);
        List<String> pathSegments = segments(path);
        if (templateSegments.size() != pathSegments.size()) {
            return false;
        }
        for (int i = 0; i < templateSegments.size(); i++) {
            String templateSegment = templateSegments.get(i);
            if (isPlaceholder(templateSegment)) {
                if (pathSegments.get(i).isEmpty()) {
                    return false;
                }
                continue;
            }
            if (!templateSegment.equals(pathSegments.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** The path-parameter values this resource binds out of a matching path. */
    public Map<String, String> pathParameters(String path) {
        Map<String, String> bound = new LinkedHashMap<>();
        List<String> templateSegments = segments(template);
        List<String> pathSegments = segments(path);
        if (templateSegments.size() != pathSegments.size()) {
            return bound;
        }
        for (int i = 0; i < templateSegments.size(); i++) {
            String templateSegment = templateSegments.get(i);
            if (isPlaceholder(templateSegment)) {
                bound.put(templateSegment.substring(1, templateSegment.length() - 1), pathSegments.get(i));
            }
        }
        return bound;
    }

    public Set<String> pathParameterNames() {
        return pathParameters(template).keySet();
    }

    private static boolean isPlaceholder(String segment) {
        return segment.startsWith("{") && segment.endsWith("}");
    }

    private static List<String> segments(String path) {
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/", -1)) {
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }
        return segments;
    }
}
