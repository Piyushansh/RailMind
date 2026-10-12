package com.railmind.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Covers the HTTP -> ... -> Kafka leg of the full correlation path this
 * phase asks to be traceable: whatever correlation ID is ambient in MDC
 * when publish() is called (set by CorrelationIdFilter for an HTTP
 * request, or by a Kafka consumer for event-driven processing - same MDC
 * key used everywhere in this codebase) ends up as the envelope's own
 * correlationId field, verbatim, not regenerated.
 */
class DomainEventPublisherTest {

    @SuppressWarnings("unchecked")
    @Test
    void publish_embedsTheAmbientCorrelationId_fromMdc() throws Exception {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        DomainEventPublisher publisher = new DomainEventPublisher(kafkaTemplate, objectMapper);

        MDC.put("correlationId", "trace-xyz-789");
        try {
            publisher.publish("booking-events", "BOOKING_CONFIRMED", Map.of("bookingId", 5));
        } finally {
            MDC.remove("correlationId");
        }

        var topicCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        var jsonCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), any(), jsonCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("booking-events");
        EventEnvelope sent = objectMapper.readValue(jsonCaptor.getValue(), EventEnvelope.class);
        assertThat(sent.correlationId()).isEqualTo("trace-xyz-789");
        assertThat(sent.eventType()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(sent.eventVersion()).isEqualTo("v1");
    }

    @SuppressWarnings("unchecked")
    @Test
    void publish_generatesAFreshCorrelationId_whenNoneIsAmbient() throws Exception {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        DomainEventPublisher publisher = new DomainEventPublisher(kafkaTemplate, objectMapper);

        MDC.clear();
        publisher.publish("audit-events", "SOME_ACTION", Map.of("x", 1));

        var jsonCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("audit-events"), any(), jsonCaptor.capture());
        EventEnvelope sent = objectMapper.readValue(jsonCaptor.getValue(), EventEnvelope.class);
        assertThat(sent.correlationId()).isNotBlank();
    }

    @SuppressWarnings("unchecked")
    @Test
    void publish_neverThrows_whenKafkaTemplateFails() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(), any(), any())).thenThrow(new RuntimeException("broker unreachable"));
        DomainEventPublisher publisher = new DomainEventPublisher(kafkaTemplate, new ObjectMapper());

        // Must not propagate - a Kafka outage must never fail the real business action that
        // already completed synchronously before this was called (see class Javadoc).
        publisher.publish("audit-events", "SOME_ACTION", Map.of("x", 1));
    }
}
