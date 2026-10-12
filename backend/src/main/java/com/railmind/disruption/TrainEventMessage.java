package com.railmind.disruption;

import java.time.Instant;
import java.util.UUID;

/**
 * The event payload published to the "train-events" Kafka topic and consumed
 * by TrainEventConsumer. Deliberately a plain, small, JSON-serializable
 * record - no entity references, so producer and consumer can evolve
 * independently and so this is trivially serializable without a schema
 * registry for this scope.
 */
public record TrainEventMessage(
        String eventId,
        String eventType,       // TRAIN_DELAYED, TRAIN_CANCELLED, PLATFORM_CHANGED, SCHEDULE_CHANGED
        Long scheduleId,
        Integer delayMinutes,   // present for TRAIN_DELAYED
        String newPlatform,     // present for PLATFORM_CHANGED
        String newDepartureTime, // present for SCHEDULE_CHANGED, ISO local time e.g. "14:30:00"
        Long triggeredByUserId, // admin who triggered the simulation, for audit trail
        Instant occurredAt,
        /** The correlation ID of whatever caused this event (usually the admin's HTTP request
         * that triggered the simulation - see CorrelationIdFilter). Distinct from eventId: eventId
         * is this Kafka event's own identity (used for kafka_event_log dedup), correlationId is
         * what ties it back to the request that caused it. Falls back to a fresh ID when there's
         * no ambient request (e.g. an event published from a background job), so this is never
         * null on the wire even though downstream consumers treat a missing/blank value gracefully. */
        String correlationId
) {
    public static TrainEventMessage newEvent(String eventType, Long scheduleId, Integer delayMinutes,
                                              String newPlatform, String newDepartureTime, Long triggeredByUserId) {
        String ambientCorrelationId = org.slf4j.MDC.get("correlationId");
        return new TrainEventMessage(UUID.randomUUID().toString(), eventType, scheduleId, delayMinutes,
                newPlatform, newDepartureTime, triggeredByUserId, Instant.now(),
                ambientCorrelationId != null ? ambientCorrelationId : UUID.randomUUID().toString());
    }
}
