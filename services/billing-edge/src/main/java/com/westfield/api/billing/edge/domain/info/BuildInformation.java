package com.westfield.api.billing.edge.domain.info;

import com.westfield.api.billing.platform.observability.MigratedFrom;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code /info} projection: which build is running, plus the server's current time (N-0044).
 *
 * <p>Two legacy properties are preserved: the endpoint ALWAYS answers, using {@code "0"} for the
 * build number and {@code "--"} for the textual fields when a value is unavailable, so monitoring
 * never has to handle a missing key.
 *
 * <p>One legacy trap is designed out rather than reproduced. The legacy defaults only fire when a
 * property is entirely unresolvable, and {@code buildInfo.properties} ships with unsubstituted Maven
 * tokens that resolve SUCCESSFULLY to their own token text — so an unfiltered build reports
 * {@code @pomBuildNumber@} as a build number and the defaults never apply (N-0011 ec, N-0044 ec).
 * ADR-0018 makes that a build failure instead of a runtime surprise: {@link #isUnsubstitutedToken}
 * detects it and the startup check refuses to serve such a value.
 *
 * <p>The timestamp carries an explicit offset. The legacy's {@code now()} takes the container's
 * implicit timezone, which is pinned nowhere (N-0044 ec 2); a timestamp whose meaning depends on a
 * container setting is not evidence of anything.
 */
@MigratedFrom(value = "km:node/N-0044", note = "setBuildInfo; defaults preserved, implicit timezone corrected")
public record BuildInformation(
        String buildNumber,
        String buildName,
        String gitCommit,
        String otherBuildInfo,
        OffsetDateTime timestamp) {

    /** The legacy default for the build number when the property cannot be resolved. */
    public static final String BUILD_NUMBER_DEFAULT = "0";

    /** The legacy default for every textual field. Two characters, exactly as today. */
    public static final String TEXT_DEFAULT = "--";

    @MigratedFrom(value = "km:node/N-0044", note = "'0' and '--' defaults; no key is ever missing")
    public static BuildInformation of(String buildNumber,
                                      String buildName,
                                      String gitCommit,
                                      String otherBuildInfo,
                                      OffsetDateTime timestamp) {
        return new BuildInformation(
                orDefault(buildNumber, BUILD_NUMBER_DEFAULT),
                orDefault(buildName, TEXT_DEFAULT),
                orDefault(gitCommit, TEXT_DEFAULT),
                orDefault(otherBuildInfo, TEXT_DEFAULT),
                timestamp);
    }

    public Map<String, Object> asMap() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("buildNumber", buildNumber);
        body.put("buildName", buildName);
        body.put("gitCommit", gitCommit);
        body.put("otherBuildInfo", otherBuildInfo);
        body.put("timestamp", timestamp == null ? null : timestamp.toString());
        return body;
    }

    /**
     * An unsubstituted build-tool token, e.g. {@code @pomBuildNumber@}. Reaching the artifact with
     * one of these is a build failure (ADR-0018), not a value to serve.
     */
    public static boolean isUnsubstitutedToken(String value) {
        if (value == null || value.length() < 2) {
            return false;
        }
        String trimmed = value.trim();
        return (trimmed.startsWith("@") && trimmed.endsWith("@"))
                || (trimmed.startsWith("${") && trimmed.endsWith("}"));
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
