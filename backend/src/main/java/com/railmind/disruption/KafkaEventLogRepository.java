package com.railmind.disruption;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KafkaEventLogRepository extends JpaRepository<KafkaEventLog, Long> {
    Optional<KafkaEventLog> findByEventId(String eventId);
}
