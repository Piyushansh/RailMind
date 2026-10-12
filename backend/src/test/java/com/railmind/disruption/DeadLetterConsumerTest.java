package com.railmind.disruption;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railmind.audit.AuditService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers dead-letter correlation ID preservation: when the original
 * TrainEventMessage can still be parsed back out of the raw DLT payload
 * (the normal case - most dead-lettered messages failed in
 * DisruptionService, not in deserialization), its real eventId/
 * correlationId are recovered and persisted. When the payload is truly
 * unparseable, synthetic identifiers are used instead of throwing.
 */
class DeadLetterConsumerTest {

    private final AuditService auditService = mock(AuditService.class);
    private final KafkaEventLogRepository kafkaEventLogRepository = mock(KafkaEventLogRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final DeadLetterConsumer consumer = new DeadLetterConsumer(auditService, kafkaEventLogRepository, objectMapper);

    @Test
    void recoversOriginalEventIdAndCorrelationId_whenPayloadIsParseable() throws Exception {
        TrainEventMessage original = TrainEventMessage.newEvent("TRAIN_DELAYED", 10L, 45, null, null, 1L);
        String payload = objectMapper.writeValueAsString(original);

        when(kafkaEventLogRepository.findByEventId(original.eventId())).thenReturn(Optional.empty());
        when(kafkaEventLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ConsumerRecord<String, String> record = new ConsumerRecord<>("train-events.DLT", 0, 5L, null, payload);
        consumer.onDeadLetter(record);

        var captor = org.mockito.ArgumentCaptor.forClass(KafkaEventLog.class);
        verify(kafkaEventLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(original.eventId());
        assertThat(captor.getValue().getCorrelationId()).isEqualTo(original.correlationId());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(MDC.get("correlationId")).as("MDC must be cleared after handling").isNull();
    }

    @Test
    void fallsBackToSyntheticId_whenPayloadIsNotParseable() {
        when(kafkaEventLogRepository.findByEventId(any())).thenReturn(Optional.empty());
        when(kafkaEventLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ConsumerRecord<String, String> record = new ConsumerRecord<>("train-events.DLT", 2, 7L, null, "not valid json at all");
        consumer.onDeadLetter(record);

        var captor = org.mockito.ArgumentCaptor.forClass(KafkaEventLog.class);
        verify(kafkaEventLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).contains("train-events.DLT").contains("2").contains("7");
        assertThat(captor.getValue().getCorrelationId()).isEqualTo(captor.getValue().getEventId());
        assertThat(MDC.get("correlationId")).isNull();
    }
}
