package com.railmind.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railmind.disruption.KafkaEventLog;
import com.railmind.disruption.KafkaEventLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * One listener method for all five topics this phase adds
 * (booking-events, payment-events, notification-events, ai-events,
 * audit-events) - they share an identical processing shape (parse envelope,
 * record it, done), so one generic consumer is the real, non-decorative
 * implementation here rather than five near-identical copies. train-events
 * keeps its own dedicated TrainEventConsumer unchanged (it drives real
 * synchronous business processing via DisruptionService; these five topics
 * are async observability/audit trails of actions that already completed
 * synchronously elsewhere - see DomainEventPublisher's Javadoc).
 *
 * Idempotency: identical to TrainEventConsumer's pattern - upsert on
 * eventId (unique-indexed), so a redelivered message updates the existing
 * kafka_event_log row instead of violating the unique constraint or
 * recording a duplicate.
 */
@Component
public class DomainEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(DomainEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final KafkaEventLogRepository kafkaEventLogRepository;
    private final com.railmind.observability.AppMetrics metrics;

    public DomainEventConsumer(ObjectMapper objectMapper, KafkaEventLogRepository kafkaEventLogRepository,
                                com.railmind.observability.AppMetrics metrics) {
        this.objectMapper = objectMapper;
        this.kafkaEventLogRepository = kafkaEventLogRepository;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = {
                    "${railmind.kafka.booking-events-topic}",
                    "${railmind.kafka.payment-events-topic}",
                    "${railmind.kafka.notification-events-topic}",
                    "${railmind.kafka.ai-events-topic}",
                    "${railmind.kafka.audit-events-topic}"
            },
            groupId = "railmind-domain-events")
    public void onEvent(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic, Acknowledgment acknowledgment)
            throws Exception {
        EventEnvelope event = objectMapper.readValue(payload, EventEnvelope.class);

        MDC.put("correlationId", event.correlationId());
        try {
            log.info("Consuming {} eventId={} topic={}", event.eventType(), event.eventId(), topic);

            KafkaEventLog eventLog = kafkaEventLogRepository.findByEventId(event.eventId()).orElseGet(KafkaEventLog::new);
            eventLog.setTopic(topic);
            eventLog.setEventId(event.eventId());
            eventLog.setEventType(event.eventType());
            eventLog.setCorrelationId(event.correlationId());
            eventLog.setPayloadSummary(truncate(String.valueOf(event.payload())));
            eventLog.setStatus("RECEIVED");
            if (eventLog.getReceivedAt() == null) {
                eventLog.setReceivedAt(Instant.now());
            }
            eventLog.setErrorMessage(null);
            eventLog = kafkaEventLogRepository.save(eventLog);

            try {
                // Processing here is deliberately just "durably record it" - see class Javadoc
                // for why these five topics don't drive further synchronous business logic the
                // way train-events does. A future consumer that needs to (e.g. a real async
                // notification-delivery worker) can be added as its own @KafkaListener on the
                // same topic without touching this one.
                eventLog.setStatus("PROCESSED");
                eventLog.setProcessedAt(Instant.now());
                kafkaEventLogRepository.save(eventLog);
                metrics.kafkaEventProcessed(topic, true);
            } catch (Exception ex) {
                eventLog.setStatus("FAILED");
                eventLog.setErrorMessage(ex.getMessage() != null
                        ? ex.getMessage().substring(0, Math.min(500, ex.getMessage().length())) : ex.getClass().getSimpleName());
                eventLog.setProcessedAt(Instant.now());
                kafkaEventLogRepository.save(eventLog);
                metrics.kafkaEventProcessed(topic, false);
                throw ex;
            }

            acknowledgment.acknowledge();
        } finally {
            MDC.remove("correlationId");
        }
    }

    private String truncate(String s) {
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
