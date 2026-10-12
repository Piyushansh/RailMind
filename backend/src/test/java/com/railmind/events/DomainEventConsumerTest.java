package com.railmind.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railmind.disruption.KafkaEventLog;
import com.railmind.disruption.KafkaEventLogRepository;
import com.railmind.observability.AppMetrics;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Mirrors TrainEventConsumerTest's coverage for the five new topics'
 * shared consumer: a new event is recorded RECEIVED->PROCESSED, a retried
 * delivery of the same eventId upserts rather than duplicating, and MDC is
 * always cleared - same properties, generalized across topics.
 */
class DomainEventConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final KafkaEventLogRepository repo = mock(KafkaEventLogRepository.class);
    private final AppMetrics metrics = mock(AppMetrics.class);
    private final Acknowledgment acknowledgment = mock(Acknowledgment.class);

    private final DomainEventConsumer consumer = new DomainEventConsumer(objectMapper, repo, metrics);

    @Test
    void newEvent_isRecordedReceivedThenProcessed_andAcknowledged() throws Exception {
        EventEnvelope event = EventEnvelope.newEvent("BOOKING_CONFIRMED", objectMapper.readTree("{\"bookingId\":5}"));
        String payload = objectMapper.writeValueAsString(event);

        when(repo.findByEventId(event.eventId())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onEvent(payload, "booking-events", acknowledgment);

        verify(acknowledgment).acknowledge();
        verify(metrics).kafkaEventProcessed("booking-events", true);

        var captor = org.mockito.ArgumentCaptor.forClass(KafkaEventLog.class);
        verify(repo, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getStatus()).isEqualTo("RECEIVED");
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo("PROCESSED");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void retriedDelivery_ofSameEventId_upsertsRatherThanInsertingASecondRow() throws Exception {
        EventEnvelope event = EventEnvelope.newEvent("PAYMENT_SUCCESS", objectMapper.readTree("{}"));
        String payload = objectMapper.writeValueAsString(event);

        KafkaEventLog existing = new KafkaEventLog();
        existing.setId(42L);
        existing.setEventId(event.eventId());
        existing.setStatus("FAILED");

        when(repo.findByEventId(event.eventId())).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onEvent(payload, "payment-events", acknowledgment);

        var captor = org.mockito.ArgumentCaptor.forClass(KafkaEventLog.class);
        verify(repo, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(row -> assertThat(row.getId()).isEqualTo(42L));
    }
}
