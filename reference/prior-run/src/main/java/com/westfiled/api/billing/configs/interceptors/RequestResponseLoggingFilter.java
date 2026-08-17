package com.westfiled.api.billing.configs.interceptors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Structured request/response entry-exit logging, replacing the Mule setRequestResponse /
 * responseLogFlow sub-flows (entryTimestamp, correlationId, requestUri, responseStatusCode,
 * entryExitElapsed).
 */
@Slf4j
@Component
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "x-correlation-id";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = firstNonBlank(request.getHeader(CORRELATION_ID_HEADER), UUID.randomUUID().toString());
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        long entryTimeMillis = System.currentTimeMillis();
        log.info("entry method={} uri={} correlationId={}", request.getMethod(), request.getRequestURI(), correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsed = System.currentTimeMillis() - entryTimeMillis;
            log.info("exit method={} uri={} correlationId={} status={} elapsedMs={}",
                    request.getMethod(), request.getRequestURI(), correlationId, response.getStatus(), elapsed);
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    private static String firstNonBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
