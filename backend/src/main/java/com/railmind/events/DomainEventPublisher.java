package com.railmind.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * One publish path for every topic added in this phase (booking-events,
 * payment-events, notification-events, ai-events, audit-events) - each
 * business service calls publish(topic, eventType, payload) instead of each
 * owning a bespoke producer class, since the envelope and the Kafka send
 * mechanics are identical across all five; only the topic name, event type,
 * and payload shape differ per call site. train-events keeps its own
 * existing TrainEventProducer unchanged (that pipeline triggers real
 * synchronous business processing via DisruptionService, not just an
 * observability record, so it stays separate).
 *
 * Publishing is deliberately fire-and-forget from the caller's perspective:
 * a Kafka outage must never take down booking/payment/notification/AI flows
 * that already succeeded via their real, synchronous service calls. If the
 * send itself fails (e.g. broker unreachable), that's logged as an ERROR
 * and swallowed here - see publish()'s catch block - rather than propagated
 * up to fail the HTTP request that triggered it.
 */
@Service
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public DomainEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(String topic, String eventType, Object payload) {
        try {
            EventEnvelope envelope = EventEnvelope.newEvent(eventType, objectMapper.valueToTree(payload));
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(topic, envelope.eventId(), json);
            log.info("Published {} eventId={} correlationId={} topic={}",
                    eventType, envelope.eventId(), envelope.correlationId(), topic);
        } catch (Exception ex) {
            // Deliberately swallowed: see class Javadoc. The real business action (the booking
            // was confirmed, the payment succeeded, the notification was already written to the
            // notifications table, etc.) has ALREADY happened via its own synchronous service
            // call by the time this is invoked - a failure here only means the observability/
            // async-workflow event didn't make it, not that the underlying action failed.
            log.error("Failed to publish {} to {}: {}", eventType, topic, ex.getMessage());
        }
    }
}
