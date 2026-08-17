package com.westfield.api.billing.edge.domain.api;

import com.westfield.api.billing.platform.observability.MigratedFrom;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Contract-first admission: everything APIkit decided about a request before any flow ran
 * (N-0001, N-0024 … N-0029).
 *
 * <p>Pure, so the whole admission surface is testable without a servlet container. The order of the
 * checks is the order the caller observes when a request violates more than one rule at once; it is
 * fixed here rather than left to a framework's dispatch order so that it cannot drift.
 *
 * <p>The failure classes are deliberately indistinguishable to a caller: all six carry a one-field
 * body and no diagnostic (N-0024 ec 1, ADR-0042). Adding detail would be a contract change.
 */
@MigratedFrom(value = "km:node/N-0001", note = "APIkit validation and its six failure classes")
public final class InboundAdmissionRule {

    private final ApiResourceTable table;

    public InboundAdmissionRule(ApiResourceTable table) {
        this.table = table;
    }

    /**
     * @param queryParameters query string values by name; a name present with an empty value counts
     *                        as supplied, exactly as APIkit treats it
     * @param acceptHeader    raw Accept header, may be null
     * @param contentType     raw Content-Type header, may be null
     * @param authorization   raw Authorization header, may be null. Declared REQUIRED by the contract
     *                        on every operation, so its absence is a contract violation answered with
     *                        400 (ADM-003-a) — not the 401 that token VALIDATION failure produces.
     *                        The two are different conditions and the legacy distinguishes them: the
     *                        spec rejects an absent header, the policy rejects a bad token.
     * @return the violation to answer with, or empty when the request is admissible
     */
    public Optional<ContractViolation> admit(String method,
                                             String path,
                                             Map<String, List<String>> queryParameters,
                                             String acceptHeader,
                                             String contentType,
                                             String authorization) {
        Optional<ApiResource> byPath = table.findByPath(path);
        if (byPath.isEmpty()) {
            return Optional.of(ContractViolation.NOT_FOUND);
        }
        Optional<ApiResource> resource = table.find(method, path);
        if (resource.isEmpty()) {
            // Path declared, verb not. The API is read-only: GET is the only verb anywhere (N-0026).
            return Optional.of(ContractViolation.METHOD_NOT_ALLOWED);
        }
        ApiResource matched = resource.get();
        if (!matched.implemented()) {
            // Spec ahead of code. 501, never 404 — the two mean different things (N-0029).
            return Optional.of(ContractViolation.NOT_IMPLEMENTED);
        }
        if (!contentTypeIsAcceptable(contentType)) {
            return Optional.of(ContractViolation.UNSUPPORTED_MEDIA_TYPE);
        }
        if (!acceptsJson(acceptHeader)) {
            return Optional.of(ContractViolation.NOT_ACCEPTABLE);
        }
        return badRequest(matched, path, queryParameters, authorization);
    }

    /**
     * Parameter length rules, the required Authorization header and the mandatory-but-unread
     * {@code environment} parameter. All three are spec-level rules with no code behind them in the
     * legacy, and all three reject before any backend call is attempted.
     */
    private Optional<ContractViolation> badRequest(ApiResource resource,
                                                   String path,
                                                   Map<String, List<String>> queryParameters,
                                                   String authorization) {
        if (authorization == null || authorization.isBlank()) {
            // Declared required by the contract on every operation. Rejected here, so no
            // implementation logic and no backend call run (ADM-002-g, SEC-001-a, SEC-001-b).
            return Optional.of(ContractViolation.BAD_REQUEST);
        }
        Map<String, String> pathParameters = resource.pathParameters(path);
        for (Map.Entry<String, Integer> rule : ApiResource.EXACT_LENGTH_PARAMETERS.entrySet()) {
            String name = rule.getKey();
            int required = rule.getValue();
            String fromPath = pathParameters.get(name);
            if (fromPath != null && fromPath.length() != required) {
                return Optional.of(ContractViolation.BAD_REQUEST);
            }
            for (String fromQuery : queryParameters.getOrDefault(name, List.of())) {
                if (fromQuery.length() != required) {
                    return Optional.of(ContractViolation.BAD_REQUEST);
                }
            }
        }
        if (resource.requiresEnvironmentParam()
                && queryParameters.getOrDefault(ApiResource.ENVIRONMENT_PARAM, List.of()).isEmpty()) {
            // Mandatory in the contract, read by nothing. Preserved deliberately (ADR-0019): the
            // requirement is observable to every caller, and relaxing it accepts requests the
            // legacy rejects, which is a contract change in the direction nobody asked for.
            return Optional.of(ContractViolation.BAD_REQUEST);
        }
        return Optional.empty();
    }

    /** Every response this API produces is JSON, so any Accept that excludes JSON is a 406. */
    private static boolean acceptsJson(String acceptHeader) {
        if (acceptHeader == null || acceptHeader.isBlank()) {
            return true;
        }
        for (String candidate : acceptHeader.toLowerCase(Locale.ROOT).split(",")) {
            String mediaType = candidate.split(";")[0].trim();
            if (mediaType.equals("*/*") || mediaType.equals("application/*")
                    || mediaType.equals("application/json") || mediaType.endsWith("+json")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every operation is a GET with no request body declared, so this can only fire when a caller
     * sends a body anyway (N-0028 ec). A body typed as JSON is tolerated, as APIkit tolerates it.
     */
    private static boolean contentTypeIsAcceptable(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        String mediaType = contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
        return mediaType.equals("application/json") || mediaType.endsWith("+json");
    }
}
