package com.westfield.api.billing.edge.domain.audit;

import com.westfield.api.billing.platform.observability.MigratedFrom;

/**
 * Replaces identifier-looking path segments with {@code X} so that account numbers, policy numbers
 * and agency codes never reach the audit log, while the endpoint shape survives for grouping.
 *
 * <p>The rule is a HEURISTIC, not a guarantee (N-0051 ec 2): a segment counts as an identifier when,
 * after hyphens are removed, it is not purely alphabetic. A purely alphabetic agency code is
 * therefore NOT masked. That is legacy behaviour, preserved deliberately (ADR-0017 makes the masked
 * URI rule part of the audit contract, so widening it would change every audit line).
 *
 * <p>Three DataWeave accidents are preserved on purpose because they are observable in the emitted
 * template, and log analytics groups on that template:
 * <ul>
 *   <li>{@code isAlpha("")} is true in DataWeave, so an EMPTY segment produced by a doubled or
 *       trailing slash is not masked and the doubled separator survives (N-0052 ec 4).</li>
 *   <li>The legacy uses {@code substringAfter(path, "/")}, which discards everything up to and
 *       including the FIRST slash. A path with no leading slash therefore loses its first segment
 *       entirely rather than having it masked (N-0052 ec 5).</li>
 *   <li>The caller re-adds the leading separator (N-0051 ec 3).</li>
 * </ul>
 *
 * <p>Pure by construction: no configuration is read and no I/O is performed (N-0052 ec 6).
 */
@MigratedFrom(value = "km:node/N-0052",
        note = "maskPath / maskPathSegment from reqresImpersonateFun.dwl; ADR-0017 makes this a contract")
public final class ResourcePathMasker {

    private static final String MASK = "X";
    private static final String SEPARATOR = "/";

    private ResourcePathMasker() {
    }

    /**
     * @param requestPath the raw request path as received
     * @return the masked template, always with a leading separator (N-0051 rule 3)
     */
    @MigratedFrom(value = "km:node/N-0051", note = "requestTemplate = '/' ++ maskPath(path)")
    public static String maskedTemplate(String requestPath) {
        return SEPARATOR + maskPath(requestPath == null ? "" : requestPath);
    }

    /**
     * Legacy {@code maskPath}: strip up to and including the first separator, then mask each
     * remaining segment independently.
     */
    @MigratedFrom(value = "km:node/N-0052", note = "maskPath; substringAfter consumes the first segment")
    static String maskPath(String requestPath) {
        int firstSeparator = requestPath.indexOf(SEPARATOR);
        // substringAfter returns "" when the separator is absent — the whole path is consumed.
        String remainder = firstSeparator < 0 ? "" : requestPath.substring(firstSeparator + 1);
        if (remainder.isEmpty()) {
            return "";
        }
        // -1 keeps trailing empty segments, which the doubled/trailing-slash edge case depends on.
        String[] segments = remainder.split(SEPARATOR, -1);
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                masked.append(SEPARATOR);
            }
            masked.append(maskSegment(segments[i]));
        }
        return masked.toString();
    }

    /**
     * Legacy {@code maskPathSegment}: mask unless the segment is purely alphabetic once hyphens are
     * removed. An empty segment is alphabetic in DataWeave and is therefore left alone.
     *
     * <p>Public because ADR-0015 requires the payload-logging path to mask identifiers with "the same
     * masking implementation the audit trail uses, so one rule governs both streams". Exposing it is
     * the mechanical form of that decision: a second, subtly different redaction rule is exactly what
     * the ADR set out to prevent.
     */
    @MigratedFrom(value = "km:node/N-0052", note = "maskPathSegment; isAlpha after removing hyphens")
    public static String maskSegment(String segment) {
        return isAlphaIgnoringHyphens(segment) ? segment : MASK;
    }

    private static boolean isAlphaIgnoringHyphens(String segment) {
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c == '-') {
                continue;
            }
            boolean asciiLetter = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
            if (!asciiLetter) {
                return false;
            }
        }
        // Vacuously true for an empty segment — this is the DataWeave behaviour, not an oversight.
        return true;
    }
}
