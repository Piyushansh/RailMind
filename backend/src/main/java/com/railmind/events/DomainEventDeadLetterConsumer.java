package com.railmind.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railmind.audit.AuditService;
import com.railmind.disruption.KafkaEventLog;
import com.railmind.disruption.KafkaEventLogRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Dead-letter handling for the five topics DomainEventConsumer listens to,
 * mirroring DeadLetterConsumer's approach for train-events but generalized
 * across topics via a regex pattern instead of one fixed topic name (these
 * five all share the same EventEnvelope shape, so one recovery method can
 * genuinely handle all of them the same way train-events' dedicated
 * DeadLetterConsumer handles its own single topic).
 */
@Component
public class DomainEventDeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(DomainEventDeadLetterConsumer.class);

    private final AuditService auditService;
    private final KafkaEventLogRepository kafkaEventLogRepository;
    private final ObjectMapper objectMapper;
    private final com.railmind.observability.AppMetrics metrics;

    public DomainEventDeadLetterConsumer(AuditService auditService, KafkaEventLogRepository kafkaEventLogRepository,
                                          ObjectMapper objectMapper, com.railmind.observability.AppMetrics metrics) {
        this.auditService = auditService;
        this.kafkaEventLogRepository = kafkaEventLogRepository;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @KafkaListener(
            topicPattern = "(booking|payment|notification|ai|audit)-events\\.DLT",
            groupId = "railmind-domain-events-dlq")
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        String recoveredEventId = null;
        String recoveredCorrelationId = null;
        try {
            EventEnvelope original = objectMapper.readValue(record.value(), EventEnvelope.class);
            recoveredEventId = original.eventId();
            recoveredCorrelationId = original.correlationId();
        } catch (Exception parseFailure) {
            log.warn("Dead-lettered domain event payload could not be parsed: {}", parseFailure.getMessage());
        }

        String eventId = recoveredEventId != null ? recoveredEventId
                : (record.key() != null ? record.key()
                        : "dlt-" + record.topic() + "-" + record.partition() + "-" + record.offset());
        String correlationId = recoveredCorrelationId != null ? recoveredCorrelationId : eventId;

        MDC.put("correlationId", correlationId);
        try {
            log.error("Dead-lettered domain event: topic={} key={} partition={} offset={}",
                    record.topic(), record.key(), record.partition(), record.offset());
            auditService.log(null, "KAFKA_MESSAGE_DEAD_LETTERED", "DomainEvent", null,
                    "topic=" + record.topic() + " key=" + record.key() + " offset=" + record.offset());

            KafkaEventLog eventLog = kafkaEventLogRepository.findByEventId(eventId).orElseGet(KafkaEventLog::new);
            eventLog.setTopic(record.topic());
            eventLog.setEventId(eventId);
            eventLog.setEventType(recoveredEventId != null ? "RECOVERED_FROM_DLT" : "UNKNOWN");
            eventLog.setCorrelationId(correlationId);
            eventLog.setPayloadSummary("partition=" + record.partition() + " offset=" + record.offset());
            eventLog.setStatus("FAILED");
            if (eventLog.getReceivedAt() == null) {
                eventLog.setReceivedAt(Instant.now());
            }
            eventLog.setErrorMessage("Exhausted retries or unparseable payload - see original topic's DLT for the raw value.");
            eventLog.setProcessedAt(Instant.now());
            kafkaEventLogRepository.save(eventLog);
            metrics.kafkaDeadLettered(record.topic());
        } finally {
            MDC.remove("correlationId");
        }
    }
}
