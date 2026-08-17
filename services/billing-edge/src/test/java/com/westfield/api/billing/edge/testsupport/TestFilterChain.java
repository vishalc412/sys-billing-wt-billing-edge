package com.westfield.api.billing.edge.testsupport;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * A filter chain assembled in the order the application assembles it, ending in a terminal that
 * stands in for the implementation.
 *
 * <p>Hand-rolled rather than borrowed from a test framework because the ORDER of the filters is the
 * behaviour under test (see {@code BillingEdgeConfiguration}): the audit funnel sits outside the
 * admission check so that a rejected request is still audited, and a helper that quietly reorders or
 * short-circuits would make that untestable.
 */
public final class TestFilterChain implements FilterChain {

    /** Stands in for the implementation the router would have reached. */
    @FunctionalInterface
    public interface Terminal {
        void handle(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;
    }

    private final List<Filter> filters;
    private final Terminal terminal;
    private int next;
    private boolean terminalReached;

    public TestFilterChain(Terminal terminal, Filter... filters) {
        this.terminal = terminal;
        this.filters = List.of(filters);
    }

    /** A terminal that does nothing, leaving the container default status in place. */
    public static Terminal doNothing() {
        return (request, response) -> {
        };
    }

    /** A terminal that fails, standing in for a failure raised after routing began. */
    public static Terminal failsWith(RuntimeException failure) {
        return (request, response) -> {
            throw failure;
        };
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
        if (next < filters.size()) {
            filters.get(next++).doFilter(request, response, this);
            return;
        }
        terminalReached = true;
        terminal.handle((HttpServletRequest) request, (HttpServletResponse) response);
    }

    /** True when the request got all the way to the stand-in implementation. */
    public boolean terminalReached() {
        return terminalReached;
    }
}
