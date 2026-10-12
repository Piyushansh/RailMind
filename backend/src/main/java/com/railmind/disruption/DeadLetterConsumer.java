package com.railmind.disruption;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railmind.audit.AuditService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Listens on the dead-letter topic that KafkaConfig's DeadLetterPublishingRecoverer
 * publishes to once a train event exhausts its retries (or fails immediately as
 * unparseable). This is the "do not simply log and ignore failed messages" mechanism
 * made concrete: every DLQ arrival gets an ERROR log line, an audit_logs row, AND a
 * kafka_event_log row, so a failed disruption event is operationally visible rather
 * than silently vanishing after retries run out.
 *
 * This does not attempt automatic reprocessing - a message that reached the DLQ
 * already failed repeatedly, and blindly retrying it again here would just recreate
 * the same failure loop. Manual/operator reprocessing (or a fix-and-replay tool) is
 * the correct next step, and is out of scope for this pass.
 */
@Component
public class DeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterConsumer.class);

    private final AuditService auditService;
    private final KafkaEventLogRepository kafkaEventLogRepository;
    private final ObjectMapper objectMapper;

    public DeadLetterConsumer(AuditService auditService, KafkaEventLogRepository kafkaEventLogRepository,
                               ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.kafkaEventLogRepository = kafkaEventLogRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${railmind.kafka.train-events-topic}.DLT", groupId = "railmind-disruption-dlq")
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        // Best-effort: the original event usually failed downstream (e.g. DisruptionService threw),
        // not because it was unparseable, so its eventId/correlationId are usually still recoverable
        // from the raw value. If parsing genuinely fails (a truly malformed payload), fall back to
        // synthetic identifiers rather than letting a dead-letter record itself throw.
        String recoveredEventId = null;
        String recoveredCorrelationId = null;
        try {
            TrainEventMessage original = objectMapper.readValue(record.value(), TrainEventMessage.class);
            recoveredEventId = original.eventId();
            recoveredCorrelationId = original.correlationId();
        } catch (Exception parseFailure) {
            log.warn("Dead-lettered payload could not be parsed back into a TrainEventMessage: {}", parseFailure.getMessage());
        }

        String eventId = recoveredEventId != null ? recoveredEventId
                : (record.key() != null ? record.key()
                        : "dlt-" + record.topic() + "-" + record.partition() + "-" + record.offset());
        String correlationId = recoveredCorrelationId != null ? recoveredCorrelationId : eventId;

        MDC.put("correlationId", correlationId);
        try {
            log.error("Dead-lettered train event: key={} partition={} offset={} value={}",
                    record.key(), record.partition(), record.offset(), record.value());
            auditService.log(null, "KAFKA_MESSAGE_DEAD_LETTERED", "TrainEvent", null,
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
        } finally {
            MDC.remove("correlationId");
        }
    }
}
