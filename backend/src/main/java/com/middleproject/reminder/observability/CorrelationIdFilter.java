package com.middleproject.reminder.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String ALB_TRACE_HEADER = "X-Amzn-Trace-Id";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";
    public static final String ALB_TRACE_ROOT_MDC_KEY = "albTraceRoot";
    private static final int MAX_HEADER_BYTES = 512;
    private static final int MAX_ROUTE_LENGTH = 512;
    private static final Pattern CANONICAL_UUID = Pattern.compile("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
    private static final Pattern ALB_ROOT = Pattern.compile("(?:^|;)Root=(1-[0-9a-fA-F]{8}-[0-9a-fA-F]{24})(?:;|$)");
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = correlationId(request.getHeader(CORRELATION_ID_HEADER));
        String albTraceRoot = albTraceRoot(request.getHeader(ALB_TRACE_HEADER));
        long startedAt = System.nanoTime();
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        if (albTraceRoot != null) {
            MDC.put(ALB_TRACE_ROOT_MDC_KEY, albTraceRoot);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            String route = route(request);
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
            MDC.put("httpMethod", request.getMethod());
            MDC.put("httpRoute", route);
            MDC.put("httpStatus", Integer.toString(response.getStatus()));
            MDC.put("elapsedMs", Long.toString(elapsedMillis));
            log.info("http_request_completed");
            MDC.remove("httpMethod");
            MDC.remove("httpRoute");
            MDC.remove("httpStatus");
            MDC.remove("elapsedMs");
            MDC.remove(CORRELATION_ID_MDC_KEY);
            MDC.remove(ALB_TRACE_ROOT_MDC_KEY);
        }
    }

    private String correlationId(String inbound) {
        if (isBounded(inbound) && CANONICAL_UUID.matcher(inbound).matches()) {
            return inbound;
        }
        return UUID.randomUUID().toString();
    }

    private String albTraceRoot(String traceHeader) {
        if (!isBounded(traceHeader)) {
            return null;
        }
        Matcher matcher = ALB_ROOT.matcher(traceHeader);
        return matcher.find() ? matcher.group(1) : null;
    }

    private boolean isBounded(String value) {
        return value != null && value.getBytes(StandardCharsets.UTF_8).length <= MAX_HEADER_BYTES;
    }

    private String route(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String value = pattern == null ? request.getRequestURI() : pattern.toString();
        return value.length() <= MAX_ROUTE_LENGTH ? value : value.substring(0, MAX_ROUTE_LENGTH);
    }
}
