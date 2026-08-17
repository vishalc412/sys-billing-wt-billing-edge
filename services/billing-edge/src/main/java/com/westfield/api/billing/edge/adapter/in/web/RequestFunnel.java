package com.westfield.api.billing.edge.adapter.in.web;

import com.westfield.api.billing.platform.observability.MigratedFrom;
import com.westfield.api.billing.platform.spi.CallerContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The per-request state the funnel carries, in the place Mule carried it: attached to the request
 * itself (N-0022).
 *
 * <p>{@link #decidedStatus} is the target's {@code vars.httpStatus}. The legacy reads that ONE
 * variable on both the success and the error response, with defaults of 200 and 500 respectively,
 * and never resets it between the router and the error handlers (N-0023, N-0022 ec 4). That is why
 * a path that sets 204 and then fails answers 204 — and why this class reads the status the same way
 * on both paths instead of recomputing it.
 */
@MigratedFrom(value = "km:node/N-0022", note = "flow variables of sapi-billing-search-main")
public final class RequestFunnel {

    /** Mule's own correlation id, echoed to the caller on every response of the main listener. */
    public static final String CORRELATION_ID_HEADER = "x-correlation-id";

    /** The caller-supplied id that stitches several API calls into one business interaction. */
    public static final String INTERACTION_CORRELATION_ID_HEADER = "x-interaction-correlation-id";

    /** Captured on the per-endpoint audit record though no API-key policy protects us (N-0036 ec 5). */
    public static final String API_KEY_HEADER = "x-api-key";

    /** ADR-0033: which system actually failed. Additive; no legacy response carries it. */
    public static final String FAILURE_ORIGIN_HEADER = "X-Billing-Failure-Origin";

    private static final String CORRELATION_ID = RequestFunnel.class.getName() + ".correlationId";
    private static final String CALLER = RequestFunnel.class.getName() + ".caller";
    private static final String AUDITED = RequestFunnel.class.getName() + ".audited";

    /** The listener default on the success path (N-0023). */
    public static final int DEFAULT_SUCCESS_STATUS = 200;

    /** The listener default on the failure path, when no status was ever decided (N-0023). */
    public static final int DEFAULT_FAILURE_STATUS = 500;

    private RequestFunnel() {
    }

    public static void putCorrelationId(HttpServletRequest request, String correlationId) {
        request.setAttribute(CORRELATION_ID, correlationId);
    }

    public static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CORRELATION_ID);
        return value == null ? null : value.toString();
    }

    public static void putCaller(HttpServletRequest request, CallerContext caller) {
        request.setAttribute(CALLER, caller);
    }

    public static CallerContext caller(HttpServletRequest request) {
        return (CallerContext) request.getAttribute(CALLER);
    }

    public static void markAudited(HttpServletRequest request) {
        request.setAttribute(AUDITED, Boolean.TRUE);
    }

    public static boolean isAudited(HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(AUDITED));
    }

    /**
     * The status to report on a FAILURE, read from the same place the success path reads it.
     *
     * <p>A path that set 204 and then failed downstream reports 204, not a recomputed 500. That is
     * legacy behaviour (N-0023 ec 1) and it is preserved deliberately — the status is a decision an
     * implementation already made, and the error path in the legacy has no way to know it was made
     * before the failure rather than after it.
     */
    @MigratedFrom(value = "km:node/N-0023", note = "the stale-status effect; preserved, not recomputed")
    public static int failureStatus(HttpServletResponse response) {
        int current = response.getStatus();
        if (current == 0 || current == DEFAULT_SUCCESS_STATUS) {
            return DEFAULT_FAILURE_STATUS;
        }
        return current;
    }
}
