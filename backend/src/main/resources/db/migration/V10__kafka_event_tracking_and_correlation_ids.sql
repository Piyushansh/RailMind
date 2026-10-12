-- Two things the spec asks the schema to support that had zero DB presence
-- before this migration:
--
-- 1. Kafka event tracking. Previously the ONLY record of a Kafka event ever
--    having existed was the Kafka topic itself (and, on failure, its DLT).
--    There was no way to query "what disruption events has this system
--    processed, and did each one succeed?" without reading Kafka directly.
--    kafka_event_log gives that a durable, queryable home.
--
-- 2. Correlation IDs. TrainEventConsumer already puts a correlation ID into
--    MDC for log correlation (see that class), but nothing persisted it -
--    once the log line scrolled away, the trail was gone. Adding a nullable
--    correlation_id column to audit_logs and ai_tool_executions lets a
--    single request/event be traced across both tables long after the fact,
--    matching the request -> correlation ID -> Spring Boot -> AI Agent ->
--    Tool -> Kafka -> Notification chain the spec describes.

CREATE TABLE kafka_event_log (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    event_id VARCHAR(100) NOT NULL,       -- TrainEventMessage.eventId() / future producers' equivalent
    event_type VARCHAR(60) NOT NULL,      -- TRAIN_DELAYED, TRAIN_CANCELLED, PLATFORM_CHANGED, SCHEDULE_CHANGED, ...
    payload_summary VARCHAR(500),         -- short human-readable summary, NOT the full raw payload
    status VARCHAR(20) NOT NULL,          -- RECEIVED, PROCESSED, FAILED
    error_message VARCHAR(500),
    received_at TIMESTAMP NOT NULL DEFAULT now(),
    processed_at TIMESTAMP
);

CREATE UNIQUE INDEX idx_kafka_event_log_event_id ON kafka_event_log(event_id);
CREATE INDEX idx_kafka_event_log_topic_status ON kafka_event_log(topic, status);

ALTER TABLE audit_logs ADD COLUMN correlation_id VARCHAR(64);
ALTER TABLE ai_tool_executions ADD COLUMN correlation_id VARCHAR(64);

CREATE INDEX idx_audit_logs_correlation ON audit_logs(correlation_id);
CREATE INDEX idx_ai_tool_executions_correlation ON ai_tool_executions(correlation_id);
