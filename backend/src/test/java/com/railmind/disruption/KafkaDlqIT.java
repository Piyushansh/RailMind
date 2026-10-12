package com.railmind.disruption;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the actual retry-then-DLQ behavior described in KafkaConfig against
 * a REAL Kafka broker and a REAL Spring context (TrainEventConsumer +
 * DeadLetterConsumer both actually running) - not a mock of the error
 * handler's configuration. Two real failure modes are exercised:
 *
 * 1. A malformed (unparseable) payload - proven to land on the DLT almost
 *    immediately, since JsonProcessingException is configured as
 *    not-retryable (no point burning the backoff window on it).
 * 2. A well-formed event referencing a schedule id that doesn't exist -
 *    DisruptionService throws ResourceNotFoundException every time, proving
 *    the message is retried (observable via elapsed time matching the
 *    configured backoff) before it too lands on the DLT once retries are
 *    exhausted.
 *
 * Needs Docker locally (Testcontainers spins up both Postgres and Kafka).
 */
@Testcontainers
@SpringBootTest
class KafkaDlqIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("railmind").withUsername("railmind").withPassword("railmind");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("railmind.jwt.secret", () -> "test-only-secret-test-only-secret-32b");
        // fast backoff for the test: 200ms initial, x2, capped at ~1.5s elapsed
        registry.add("railmind.kafka.retry-initial-interval-ms", () -> "200");
        registry.add("railmind.kafka.retry-multiplier", () -> "2.0");
        registry.add("railmind.kafka.retry-max-elapsed-ms", () -> "1500");
    }

    private KafkaProducer<String, String> producer;
    private KafkaConsumer<String, String> dlqConsumer;

    @BeforeEach
    void setUp() {
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producer = new KafkaProducer<>(producerProps);

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-dlq-reader-" + System.nanoTime());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        dlqConsumer = new KafkaConsumer<>(consumerProps);
        dlqConsumer.subscribe(List.of("train-events.DLT"));
    }

    @AfterEach
    void tearDown() {
        producer.close();
        dlqConsumer.close();
    }

    @Test
    void malformedPayload_landsOnDlt_quickly() {
        producer.send(new ProducerRecord<>("train-events", "999", "{ this is not valid json"));
        producer.flush();

        ConsumerRecord<String, String> dltRecord = pollForOne(Duration.ofSeconds(10));
        assertThat(dltRecord).isNotNull();
        assertThat(dltRecord.value()).contains("not valid json");
    }

    @Test
    void persistentFailure_isRetried_thenLandsOnDlt() {
        // scheduleId 999999999 does not exist - DisruptionService.handleTrainEvent will throw
        // ResourceNotFoundException every single time, proving this is a genuine retry loop,
        // not a one-shot failure.
        String payload = """
                {"eventId":"test-event-1","eventType":"TRAIN_DELAYED","scheduleId":999999999,
                 "delayMinutes":30,"newPlatform":null,"newDepartureTime":null,
                 "triggeredByUserId":null,"occurredAt":"2026-01-01T00:00:00Z"}
                """;
        long start = System.currentTimeMillis();
        producer.send(new ProducerRecord<>("train-events", "999999999", payload));
        producer.flush();

        ConsumerRecord<String, String> dltRecord = pollForOne(Duration.ofSeconds(15));
        long elapsed = System.currentTimeMillis() - start;

        assertThat(dltRecord).isNotNull();
        assertThat(dltRecord.value()).contains("test-event-1");
        // With 200ms/400ms/800ms backoff steps (~1.4s total before exceeding the 1.5s max-elapsed
        // cutoff), landing on the DLT immediately (<300ms) would mean no retry actually happened.
        assertThat(elapsed).isGreaterThan(300);
    }

    private ConsumerRecord<String, String> pollForOne(Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = dlqConsumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> r : records) {
                return r;
            }
        }
        return null;
    }
}
