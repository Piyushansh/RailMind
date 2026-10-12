package com.railmind.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Gives every HTTP request a correlation ID, in MDC for the duration of that
 * request, so log lines, AuditService entries, and ai_tool_executions rows
 * created while handling it can all be tied back together (spec section 82:
 * Request -> Correlation ID -> Spring Boot -> AI Agent -> Tool -> Kafka ->
 * Notification). This is a plain servlet filter, registered outside Spring
 * Security's own filter chain entirely (see SecurityConfig, unchanged by
 * this class) - it runs for every request, authenticated or not, and never
 * makes an authorization decision, so it cannot affect existing auth
 * behaviour.
 *
 * TrainEventConsumer already does the equivalent for Kafka-driven work
 * (MDC.put("correlationId", event.eventId())) - this filter is the HTTP-side
 * counterpart using the same MDC key, not a second/competing mechanism.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER_NAME);
        String correlationId = (incoming != null && !incoming.isBlank()) ? incoming.trim() : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER_NAME, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always cleared, success or exception, so nothing about this request's
            // correlation ID can leak into whatever the next request on this thread does.
            MDC.remove(MDC_KEY);
        }
    }
}
