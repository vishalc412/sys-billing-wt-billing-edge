package com.westfield.api.billing.edge.adapter.in.web;

import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.platform.observability.MigratedFrom;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Where the documentation console lives (N-0031, ADR-0038).
 *
 * <p>The legacy hardcodes {@code /console/*} and its listener path {@code /*} overlaps it; Mule
 * resolves to the more specific path, so console traffic bypasses {@code setRequestResponse} and is
 * never audited (N-0022 ec 2, N-0031 ec 2). The overlap is reproduced here as an explicit predicate
 * rather than as an accident of matching order, and the path itself is configuration now.
 */
@MigratedFrom(value = "km:node/N-0031", note = "the /console/* overlap with the main listener path")
public final class ConsolePaths {

    private ConsolePaths() {
    }

    public static boolean isConsole(HttpServletRequest request, BillingEdgeProperties properties) {
        return isConsole(request.getRequestURI(), properties.getConsole().getPath());
    }

    public static boolean isConsole(String path, String consolePath) {
        if (path == null || consolePath == null || consolePath.isBlank()) {
            return false;
        }
        String normalised = consolePath.endsWith("/")
                ? consolePath.substring(0, consolePath.length() - 1)
                : consolePath;
        return path.equals(normalised) || path.startsWith(normalised + "/");
    }
}
