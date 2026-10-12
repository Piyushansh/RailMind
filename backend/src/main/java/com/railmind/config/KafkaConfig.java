package com.railmind.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Real retry-with-backoff and dead-letter handling for every @KafkaListener
 * in the app (currently just TrainEventConsumer, but this applies uniformly
 * to any future consumer using the default container factory).
 *
 * Behavior:
 * - A transient failure (e.g. a DB blip inside DisruptionService) is retried
 *   with exponential backoff (default: 1s, 2s, 4s, 8s.. up to ~15s elapsed)
 *   before giving up.
 * - Once retries are exhausted, OR immediately for a exception we know is
 *   never going to succeed on retry (a malformed/unparseable payload), the
 *   record is published to "{original-topic}.DLT" via
 *   DeadLetterPublishingRecoverer and the original offset is committed - so
 *   a poison-pill message can never block the partition forever, and nothing
 *   is silently dropped: it's inspectable on the DLT topic (see
 *   DeadLetterConsumer, which logs and audits every DLT arrival).
 */
@Configuration
public class KafkaConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${railmind.kafka.retry-initial-interval-ms}") long initialIntervalMs,
            @Value("${railmind.kafka.retry-multiplier}") double multiplier,
            @Value("${railmind.kafka.retry-max-elapsed-ms}") long maxElapsedMs
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));

        ExponentialBackOff backOff = new ExponentialBackOff(initialIntervalMs, multiplier);
        backOff.setMaxElapsedTime(maxElapsedMs);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // A malformed payload will never parse successfully no matter how many times we retry -
        // send it straight to the DLT instead of wasting the whole backoff window on it.
        handler.addNotRetryableExceptions(JsonProcessingException.class);
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory, DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}
