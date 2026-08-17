
package com.westfield.api.billing.platform.errors;

import java.net.URI;
import java.util.Map;

/**
 * RFC 9457 problem detail.
 *
 * <p>Scope is deliberately narrow (ADR-0042): this shape is used ONLY for responses the legacy never
 * produced at all — principally the token-rejection response of ADR-0013. It does NOT replace any
 * legacy error body. The three legacy shapes (the bare {@code {"message": ...}}, the
 * {@code error_Flow} shape, and whatever {@code sapi-common-errorhandler} emits) are preserved
 * byte-for-byte and are documented in the frozen contracts.
 */
public record BillingProblem(
        URI type,
        String title,
        int status,
        String detail,
        URI instance,
        Map<String, Object> extensions) {
}
