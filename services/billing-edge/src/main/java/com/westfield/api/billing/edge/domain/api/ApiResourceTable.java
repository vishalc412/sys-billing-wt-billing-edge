package com.westfield.api.billing.edge.domain.api;

import com.westfield.api.billing.platform.observability.MigratedFrom;

import java.util.List;
import java.util.Optional;

/**
 * The declared API surface — the target's replacement for {@code apikit:config} routing against
 * {@code sapi-billing-search.raml} (N-0001).
 *
 * <p>Eight operations plus {@code /info}, every one of them a GET. Five of the nine emit the
 * per-endpoint audit record; the three {@code /primaryAccount*} resources do not, and ADR-0017
 * preserves that gap rather than closing it (N-0045 ec 1).
 *
 * <p>The {@code environment} query parameter is mandatory on four resources and on nothing else.
 * Nothing reads it. It is kept because APIkit rejects a request that omits it, and a caller that
 * has always sent it would not notice, while a caller that has never sent it is today already
 * failing (ADR-0019 preserves the requirement).
 */
@MigratedFrom(value = "km:node/N-0001", note = "the RAML resource surface as a routing table")
public final class ApiResourceTable {

    /** The externally-facing resources, in contract order. */
    private static final List<ApiResource> RESOURCES = List.of(
            new ApiResource("GET", "/externalPrimaryAccount/{billingAccountNumber}/account-billing", true, true, true),
            new ApiResource("GET", "/externalPrimaryAccount/{policyNumber}/policy-billing", true, true, true),
            new ApiResource("GET", "/pastDueToday/{agencyCode}", true, true, true),
            new ApiResource("GET", "/pendingCancelToday/{agencyCode}", true, true, true),
            new ApiResource("GET", "/info", false, true, true),
            // The three /primaryAccount resources carry no environment trait (N-0045 ec 2) and emit
            // no per-endpoint audit record (ADR-0017).
            new ApiResource("GET", "/primaryAccount", false, false, true),
            new ApiResource("GET", "/primaryAccount/transactions", false, false, true),
            new ApiResource("GET", "/primaryAccount/policy/escrow/transactions", false, false, true));

    private final List<ApiResource> resources;

    public ApiResourceTable() {
        this(RESOURCES);
    }

    /** Test seam: a table containing a declared-but-unimplemented resource exercises the 501 path. */
    public ApiResourceTable(List<ApiResource> resources) {
        this.resources = List.copyOf(resources);
    }

    public List<ApiResource> resources() {
        return resources;
    }

    /** The resource matching method and path, if the contract declares one. */
    public Optional<ApiResource> find(String method, String path) {
        return resources.stream().filter(r -> r.matches(method, path)).findFirst();
    }

    /**
     * A resource whose PATH is declared, whatever the verb. The distinction is the whole difference
     * between 404 (path unknown) and 405 (path known, verb not declared) — N-0025 vs N-0026.
     */
    public Optional<ApiResource> findByPath(String path) {
        return resources.stream().filter(r -> r.pathMatches(path)).findFirst();
    }

    /**
     * The URI template a request matched, for the audit record. Falls back to the raw path when the
     * contract declares no such resource, so an audit line is never missing the field.
     */
    public String uriTemplateFor(String method, String path) {
        return find(method, path).map(ApiResource::template).orElse(path);
    }
}
