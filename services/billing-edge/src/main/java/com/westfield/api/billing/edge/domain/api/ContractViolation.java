package com.westfield.api.billing.edge.domain.api;

import com.westfield.api.billing.platform.observability.MigratedFrom;

/**
 * The six routing failure classes the contract router produces, with the EXACT legacy status and
 * body (N-0024 … N-0029).
 *
 * <p>The body is one field and a fixed string. A caller cannot tell which of several possible
 * contract violations occurred, and that opacity is deliberate legacy behaviour preserved by
 * ADR-0042 — any consumer parsing a reason out of the body gets nothing useful today and must
 * continue to get nothing useful, because adding detail is a contract change.
 *
 * <p>These bodies deliberately DO NOT match the {@code commonError} type the legacy RAML published
 * (faultActor/faultCode/faultMessage/faultDetail/faultTime/innerFault). The application has never
 * emitted that type. ADR-0042 preserves the shapes and rewrites the contract to match them, rather
 * than the other way round; ADM-003-g asserts the divergence so it cannot be "corrected" silently.
 *
 * <p>405 carries NO {@code Allow} header, which HTTP requires (N-0026 ec). Preserved.
 */
@MigratedFrom(value = "km:node/N-0024",
        note = "N-0024..N-0029 collapsed to one table; exact strings frozen by ADR-0042")
public enum ContractViolation {

    /** Parameter length, missing required query parameter, or missing Authorization header. */
    @MigratedFrom("km:node/N-0024")
    BAD_REQUEST(400, "Bad request"),

    /** Path the contract does not define. Routing-level only — see {@link #NOT_FOUND} note. */
    @MigratedFrom("km:node/N-0025")
    NOT_FOUND(404, "Resource not found"),

    /** Known resource, verb the contract does not define. The API is read-only: GET only. */
    @MigratedFrom("km:node/N-0026")
    METHOD_NOT_ALLOWED(405, "Method not allowed"),

    /** Accept header cannot be satisfied by application/json. */
    @MigratedFrom("km:node/N-0027")
    NOT_ACCEPTABLE(406, "Not acceptable"),

    /** Request body in a media type the contract does not accept. */
    @MigratedFrom("km:node/N-0028")
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported media type"),

    /** Resource declared in the contract with no implementation behind it. Not a 404. */
    @MigratedFrom("km:node/N-0029")
    NOT_IMPLEMENTED(501, "Not Implemented");

    private final int status;
    private final String message;

    ContractViolation(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public int status() {
        return status;
    }

    /** The exact legacy string. Changing it is a contract change (ADR-0042). */
    public String message() {
        return message;
    }
}
