package com.railmind.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Custom business counters, on top of what Spring Boot Actuator already
 * gives for free once micrometer-registry-prometheus is on the classpath
 * (http.server.requests - API latency/count/status per endpoint - needs no
 * code here at all, it's auto-instrumented). These are the metrics that
 * only make sense with domain knowledge of what a "booking" or "RAG query"
 * is, so Micrometer can't infer them automatically.
 */
@Component
public class AppMetrics {

    private final MeterRegistry registry;

    public AppMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void bookingCreated() {
        Counter.builder("railmind.bookings.created").register(registry).increment();
    }

    public void bookingConfirmed() {
        Counter.builder("railmind.bookings.confirmed").register(registry).increment();
    }

    public void bookingCancelled() {
        Counter.builder("railmind.bookings.cancelled").register(registry).increment();
    }

    public void paymentResult(String status) {
        Counter.builder("railmind.payments.total").tag("status", status).register(registry).increment();
    }

    public void kafkaEventProcessed(String topic, boolean success) {
        Counter.builder("railmind.kafka.events.total")
                .tag("topic", topic).tag("outcome", success ? "success" : "failure")
                .register(registry).increment();
    }

    public void kafkaDeadLettered(String topic) {
        Counter.builder("railmind.kafka.dead_lettered.total").tag("topic", topic).register(registry).increment();
    }

    public void aiToolExecution(String agentName, String toolName, boolean success) {
        Counter.builder("railmind.ai.tool_executions.total")
                .tag("agent", agentName).tag("tool", toolName).tag("outcome", success ? "success" : "failure")
                .register(registry).increment();
    }

    public void ragQuery(boolean sufficientInformation) {
        Counter.builder("railmind.rag.queries.total")
                .tag("grounded", String.valueOf(sufficientInformation))
                .register(registry).increment();
    }

    public void error(String exceptionType) {
        Counter.builder("railmind.errors.total").tag("type", exceptionType).register(registry).increment();
    }
}
