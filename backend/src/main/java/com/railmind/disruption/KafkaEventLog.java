package com.railmind.disruption;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Durable record of a Kafka event's lifecycle (received -> processed/failed),
 * backing kafka_event_log (see V10 migration). Written by TrainEventConsumer
 * and DeadLetterConsumer - this is what lets an operator answer "what
 * disruption events has this system actually seen, and what happened to
 * each one?" without reading Kafka topics directly.
 */
@Getter @Setter
@Entity
@Table(name = "kafka_event_log")
public class KafkaEventLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String topic;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload_summary")
    private String payloadSummary;

    @Column(name = "correlation_id")
    private String correlationId;

    /** RECEIVED, PROCESSED, FAILED */
    @Column(nullable = false)
    private String status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;
}
