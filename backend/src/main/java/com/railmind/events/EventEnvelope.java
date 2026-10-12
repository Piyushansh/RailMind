package com.railmind.events;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * The common shape every event on every topic in this app carries
 * (booking-events, payment-events, train-events, notification-events,
 * ai-events, audit-events). train-events predates this class and uses its
 * own TrainEventMessage record instead of this one - that record already
 * independently carries the same five metadata fields (eventId, eventType,
 * correlationId, occurredAt, plus its own typed payload fields) and is
 * left as-is per "do not rebuild from scratch"; this envelope is what
 * every topic added in this phase uses, and is the one DomainEventConsumer
 * / DomainEventDeadLetterConsumer know how to parse generically regardless
 * of which of the five topics a message came from.
 */
public record EventEnvelope(
        String eventId,
        String eventType,
        String eventVersion,
        String correlationId,
        Instant occurredAt,
        JsonNode payload
) {
    public static EventEnvelope newEvent(String eventType, JsonNode payload) {
        String correlationId = org.slf4j.MDC.get("correlationId");
        return new EventEnvelope(UUID.randomUUID().toString(), eventType, "v1",
                correlationId != null ? correlationId : UUID.randomUUID().toString(), Instant.now(), payload);
    }
}
