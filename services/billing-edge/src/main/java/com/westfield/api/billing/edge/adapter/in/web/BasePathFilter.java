package com.westfield.api.billing.edge.adapter.in.web;

import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.platform.observability.MigratedFrom;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;

/**
 * Applies the externally visible base path (ADM-004-c, DEF-0111).
 *
 * <p>{@code billing.api.base-path} is validated and logged at startup but was never applied, so a
 * deployment that declares {@code /sapi-billing/v1} (the legacy local listener path) still served
 * at the root and {@code /sapi-billing/v1/info} answered 404. This filter makes the configured base
 * path resolve: a request whose path begins with the base path is served as if it had been made to
 * the stripped path, so {@code /sapi-billing/v1/info} routes to the same controller as {@code /info}.
 *
 * <p>The root continues to resolve too. The legacy local listener path is the base path, but every
 * acceptance criterion and probe that exercises the assembled container addresses the root, and
 * retiring the root would be a contract change nobody asked for. Serving both is the alias form of
 * "apply it to the mappings" the packet allows.
 *
 * <p>This filter sits outermost-1 (before the correlation id filter), so every downstream filter
 * and controller — including console-path detection — sees the stripped path uniformly. It does
 * nothing when the base path is the root ({@code /}).
 */
@MigratedFrom(value = "km:node/N-0022",
        note = "ADM-004-c: the legacy listener base path is applied to the mappings")
public class BasePathFilter extends OncePerRequestFilter {

    private final String basePath;

    public BasePathFilter(BillingEdgeProperties properties) {
        this.basePath = normalise(properties.getApi().getBasePath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        HttpServletRequest effective = request;
        if (!basePath.isEmpty()) {
            String stripped = stripBasePath(request.getRequestURI());
            if (stripped != null) {
                effective = new BasePathStrippedRequest(request, stripped);
            }
        }
        chain.doFilter(effective, response);
    }

    /** @return the path with the base path prefix removed, or null when the request is not under it. */
    private String stripBasePath(String requestUri) {
        if (requestUri == null) {
            return null;
        }
        if (requestUri.equals(basePath)) {
            return "/";
        }
        String prefix = basePath + "/";
        if (requestUri.startsWith(prefix)) {
            return requestUri.substring(basePath.length());
        }
        return null;
    }

    /** Trailing slash removed; a blank or root base path becomes "" (no stripping). */
    private static String normalise(String basePath) {
        if (basePath == null || basePath.isBlank() || "/".equals(basePath)) {
            return "";
        }
        return basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
    }

    /**
     * Wraps the request so every consumer of the request URI — routing, admission, console
     * detection, audit — sees the path with the base path stripped. The context path stays empty,
     * so {@code pathWithinApplication = requestURI - contextPath} resolves to the stripped path.
     */
    private static final class BasePathStrippedRequest extends HttpServletRequestWrapper {
        private final String requestUri;

        BasePathStrippedRequest(HttpServletRequest delegate, String requestUri) {
            super(delegate);
            this.requestUri = requestUri;
        }

        @Override
        public String getRequestURI() {
            return requestUri;
        }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer url = super.getRequestURL();
            // Rebuild the URL from the stripped path so anything that materialises it stays consistent.
            String full = url.toString();
            int schemeEnd = full.indexOf("://") + 3;
            int pathStart = full.indexOf('/', schemeEnd);
            StringBuffer rebuilt = new StringBuffer();
            rebuilt.append(pathStart < 0 ? full : full.substring(0, pathStart)).append(requestUri);
            return rebuilt;
        }

        @Override
        public String getServletPath() {
            return requestUri;
        }

        @Override
        public String getPathInfo() {
            return null;
        }
    }
}