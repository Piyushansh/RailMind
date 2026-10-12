package com.railmind.disruption;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the properties Phase 2 called out explicitly for Kafka event
 * logging: a new event gets RECEIVED->PROCESSED rows, a retried delivery of
 * the SAME eventId upserts rather than violating the unique index, a
 * failing event is recorded FAILED and still rethrows (so the container's
 * retry/DLQ handling isn't short-circuited), and the event's own
 * correlationId (not eventId) is what ends up in MDC and in the persisted
 * row.
 */
class TrainEventConsumerTest {

    private final DisruptionService disruptionService = mock(DisruptionService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final KafkaEventLogRepository kafkaEventLogRepository = mock(KafkaEventLogRepository.class);
    private final Acknowledgment acknowledgment = mock(Acknowledgment.class);

    private final TrainEventConsumer consumer = new TrainEventConsumer(disruptionService, objectMapper, kafkaEventLogRepository);

    @Test
    void newEvent_isRecordedReceivedThenProcessed_andAcknowledged() throws Exception {
        TrainEventMessage event = TrainEventMessage.newEvent("TRAIN_DELAYED", 10L, 45, null, null, 1L);
        String payload = objectMapper.writeValueAsString(event);

        when(kafkaEventLogRepository.findByEventId(event.eventId())).thenReturn(Optional.empty());
        when(kafkaEventLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onTrainEvent(payload, acknowledgment);

        verify(disruptionService).handleTrainEvent(any());
        verify(acknowledgment).acknowledge();

        var captor = org.mockito.ArgumentCaptor.forClass(KafkaEventLog.class);
        verify(kafkaEventLogRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getStatus()).isEqualTo("RECEIVED");
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo("PROCESSED");
        assertThat(captor.getAllValues().get(1).getCorrelationId()).isEqualTo(event.correlationId());
        assertThat(MDC.get("correlationId")).as("MDC must be cleared after processing").isNull();
    }

    @Test
    void retriedDelivery_ofSameEventId_upsertsRatherThanInsertingASecondRow() throws Exception {
        TrainEventMessage event = TrainEventMessage.newEvent("TRAIN_DELAYED", 10L, 45, null, null, 1L);
        String payload = objectMapper.writeValueAsString(event);

        KafkaEventLog existing = new KafkaEventLog();
        existing.setId(99L);
        existing.setEventId(event.eventId());
        existing.setStatus("FAILED"); // first attempt failed; this is the retry

        when(kafkaEventLogRepository.findByEventId(event.eventId())).thenReturn(Optional.of(existing));
        when(kafkaEventLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onTrainEvent(payload, acknowledgment);

        var captor = org.mockito.ArgumentCaptor.forClass(KafkaEventLog.class);
        verify(kafkaEventLogRepository, atLeastOnce()).save(captor.capture());
        // Every saved instance must be the SAME row (id=99), never a fresh one - that's what
        // makes this an upsert rather than a second insert that would violate the unique index.
        assertThat(captor.getAllValues()).allSatisfy(row -> assertThat(row.getId()).isEqualTo(99L));
    }

    @Test
    void failingEvent_isRecordedFailed_andStillRethrows_soRetryAndDlqStillHappen() throws Exception {
        TrainEventMessage event = TrainEventMessage.newEvent("TRAIN_CANCELLED", 10L, null, null, null, 1L);
        String payload = objectMapper.writeValueAsString(event);

        when(kafkaEventLogRepository.findByEventId(event.eventId())).thenReturn(Optional.empty());
        when(kafkaEventLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("simulated processing failure")).when(disruptionService).handleTrainEvent(any());

        assertThatThrownBy(() -> consumer.onTrainEvent(payload, acknowledgment))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated processing failure");

        verify(acknowledgment, never()).acknowledge();

        var captor = org.mockito.ArgumentCaptor.forClass(KafkaEventLog.class);
        verify(kafkaEventLogRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo("FAILED");
        assertThat(captor.getAllValues().get(1).getErrorMessage()).contains("simulated processing failure");
        assertThat(MDC.get("correlationId")).as("MDC must be cleared even after a failure").isNull();
    }
}
