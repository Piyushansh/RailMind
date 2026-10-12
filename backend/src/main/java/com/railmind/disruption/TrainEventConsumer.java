package com.railmind.disruption;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Consumes "train-events" and delegates to DisruptionService, which holds
 * ALL the business logic (updating the schedule, finding affected bookings,
 * alternatives, notifications). This class only does deserialization,
 * correlation-ID logging, kafka_event_log bookkeeping, and manual ack -
 * exactly what a Kafka listener should and shouldn't do.
 *
 * Retry and dead-lettering are handled by the container's DefaultErrorHandler
 * (see KafkaConfig), not here: if this method throws (a parse failure OR a
 * DisruptionService failure), the container retries with exponential backoff
 * and publishes to "train-events.DLT" once retries are exhausted (or
 * immediately for an unparseable payload, which is never going to succeed on
 * retry). That's why this listener does NOT catch-and-acknowledge on parse
 * failure the way an earlier version did - swallowing it here would hide it
 * from the DLQ entirely.
 */
@Component
public class TrainEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TrainEventConsumer.class);

    private final DisruptionService disruptionService;
    private final ObjectMapper objectMapper;
    private final KafkaEventLogRepository kafkaEventLogRepository;

    public TrainEventConsumer(DisruptionService disruptionService, ObjectMapper objectMapper,
                               KafkaEventLogRepository kafkaEventLogRepository) {
        this.disruptionService = disruptionService;
        this.objectMapper = objectMapper;
        this.kafkaEventLogRepository = kafkaEventLogRepository;
    }

    @KafkaListener(topics = "${railmind.kafka.train-events-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onTrainEvent(String payload, Acknowledgment acknowledgment) throws Exception {
        TrainEventMessage event = objectMapper.readValue(payload, TrainEventMessage.class);

        // Preserve the correlation ID that came WITH the event (usually the admin HTTP request
        // that triggered the simulation - see CorrelationIdFilter + TrainEventMessage.newEvent),
        // rather than the event's own eventId. eventId identifies this Kafka event for dedup;
        // correlationId is what ties it back to whatever caused it, and is what a caller-supplied
        // trace should follow through Kafka processing.
        String correlationId = event.correlationId() != null ? event.correlationId() : event.eventId();
        MDC.put("correlationId", correlationId);
        try {
            log.info("Consuming {} for schedule {}", event.eventType(), event.scheduleId());

            // Upsert on eventId: a retried delivery (same eventId, redelivered by the container
            // after a failure) must update the existing row, not insert a second one - eventId
            // is uniquely indexed precisely so one Kafka event has exactly one durable record,
            // whose status reflects the MOST RECENT processing attempt.
            KafkaEventLog eventLog = kafkaEventLogRepository.findByEventId(event.eventId())
                    .orElseGet(KafkaEventLog::new);
            eventLog.setTopic("train-events");
            eventLog.setEventId(event.eventId());
            eventLog.setEventType(event.eventType());
            eventLog.setCorrelationId(correlationId);
            eventLog.setPayloadSummary("scheduleId=" + event.scheduleId()
                    + (event.delayMinutes() != null ? " delayMinutes=" + event.delayMinutes() : "")
                    + (event.newPlatform() != null ? " newPlatform=" + event.newPlatform() : ""));
            eventLog.setStatus("RECEIVED");
            if (eventLog.getReceivedAt() == null) {
                eventLog.setReceivedAt(Instant.now());
            }
            eventLog.setErrorMessage(null);
            eventLog = kafkaEventLogRepository.save(eventLog);

            try {
                disruptionService.handleTrainEvent(event);
                eventLog.setStatus("PROCESSED");
                eventLog.setProcessedAt(Instant.now());
                kafkaEventLogRepository.save(eventLog);
            } catch (Exception ex) {
                eventLog.setStatus("FAILED");
                eventLog.setErrorMessage(ex.getMessage() != null
                        ? ex.getMessage().substring(0, Math.min(500, ex.getMessage().length())) : ex.getClass().getSimpleName());
                eventLog.setProcessedAt(Instant.now());
                kafkaEventLogRepository.save(eventLog);
                throw ex;
            }

            acknowledgment.acknowledge();
        } finally {
            MDC.remove("correlationId");
        }
    }
}
