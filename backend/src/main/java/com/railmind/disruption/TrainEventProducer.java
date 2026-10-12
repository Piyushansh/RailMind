package com.railmind.disruption;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes train disruption events. Key = scheduleId.toString() so all
 * events for the same schedule land on the same partition and are processed
 * in order by the consumer. Value is a JSON string (StringSerializer on both
 * sides) rather than a Kafka-specific Avro/JsonSerializer, keeping the
 * producer/consumer contract simple and inspectable with plain Kafka CLI
 * tools during local development.
 */
@Component
public class TrainEventProducer {

    private static final Logger log = LoggerFactory.getLogger(TrainEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public TrainEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                               @Value("${railmind.kafka.train-events-topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public void publish(TrainEventMessage event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, event.scheduleId().toString(), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish train event {} for schedule {}: {}",
                                    event.eventType(), event.scheduleId(), ex.getMessage());
                        } else {
                            log.info("Published {} for schedule {} (eventId={})",
                                    event.eventType(), event.scheduleId(), event.eventId());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to serialize train event: {}", e.getMessage());
            throw new IllegalStateException("Could not publish disruption event", e);
        }
    }
}
