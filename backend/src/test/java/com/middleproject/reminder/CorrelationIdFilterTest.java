package com.middleproject.reminder;

import com.middleproject.reminder.observability.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesCanonicalCorrelationIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletResponse response = execute(request(), (request, servletResponse) -> { });

        assertDoesNotThrow(() -> UUID.fromString(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)));
    }

    @Test
    void preservesCanonicalInboundCorrelationIdAndReturnsIt() throws Exception {
        String correlationId = UUID.randomUUID().toString();
        MockHttpServletRequest request = request();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);

        MockHttpServletResponse response = execute(request, (servletRequest, servletResponse) ->
                assertEquals(correlationId, MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)));

        assertEquals(correlationId, response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER));
    }

    @Test
    void replacesMalformedAndOversizedCorrelationIds() throws Exception {
        MockHttpServletRequest malformed = request();
        malformed.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "not-a-uuid");
        MockHttpServletResponse malformedResponse = execute(malformed, (request, response) -> { });

        MockHttpServletRequest oversized = request();
        oversized.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "a".repeat(513));
        MockHttpServletResponse oversizedResponse = execute(oversized, (request, response) -> { });

        assertNotEquals("not-a-uuid", malformedResponse.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER));
        assertDoesNotThrow(() -> UUID.fromString(malformedResponse.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)));
        assertDoesNotThrow(() -> UUID.fromString(oversizedResponse.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)));
    }

    @Test
    void extractsValidAlbTraceRootOnlyFromBoundedHeader() throws Exception {
        String root = "1-5f84c7a1-0123456789abcdef01234567";
        MockHttpServletRequest request = request();
        request.addHeader(CorrelationIdFilter.ALB_TRACE_HEADER, "Self=1-ignored;Root=" + root + ";Parent=abc");

        execute(request, (servletRequest, servletResponse) ->
                assertEquals(root, MDC.get(CorrelationIdFilter.ALB_TRACE_ROOT_MDC_KEY)));
    }

    @Test
    void omitsInvalidAndOversizedAlbTraceRoot() throws Exception {
        MockHttpServletRequest invalid = request();
        invalid.addHeader(CorrelationIdFilter.ALB_TRACE_HEADER, "Root=not-valid");
        execute(invalid, (request, response) -> assertNull(MDC.get(CorrelationIdFilter.ALB_TRACE_ROOT_MDC_KEY)));

        String root = "1-5f84c7a1-0123456789abcdef01234567";
        MockHttpServletRequest oversized = request();
        oversized.addHeader(CorrelationIdFilter.ALB_TRACE_HEADER, "Root=" + root + ";" + "x".repeat(513));
        assertTrue(oversized.getHeader(CorrelationIdFilter.ALB_TRACE_HEADER).getBytes(StandardCharsets.UTF_8).length > 512);
        execute(oversized, (request, response) -> assertNull(MDC.get(CorrelationIdFilter.ALB_TRACE_ROOT_MDC_KEY)));
    }

    @Test
    void boundsFallbackRouteBeforeLogging() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/" + "a".repeat(1024));
        execute(request, (servletRequest, servletResponse) -> { });

        assertNull(MDC.get("httpRoute"));
    }

    @Test
    void clearsMdcAfterSuccessAndFailure() throws Exception {
        execute(request(), (request, response) -> assertNotNull(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)));
        assertNull(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
        assertNull(MDC.get(CorrelationIdFilter.ALB_TRACE_ROOT_MDC_KEY));

        assertThrows(ServletException.class, () -> execute(request(), (request, response) -> {
            assertNotNull(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
            throw new ServletException("filter-chain-failure");
        }));
        assertNull(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
        assertNull(MDC.get(CorrelationIdFilter.ALB_TRACE_ROOT_MDC_KEY));
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/reminders");
        request.setAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingPattern", "/api/reminders");
        return request;
    }

    private MockHttpServletResponse execute(MockHttpServletRequest request, FilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
