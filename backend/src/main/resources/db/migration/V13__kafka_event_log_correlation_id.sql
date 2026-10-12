-- Completes Phase 2's correlation-ID coverage: V10 added correlation_id to
-- audit_logs and ai_tool_executions, but not to kafka_event_log itself.
-- event_id already uniquely identifies a Kafka event for dedup purposes,
-- but it is NOT the same thing as correlation_id: correlation_id is what
-- ties a Kafka event back to whatever caused it (usually the admin HTTP
-- request that triggered a simulation - see CorrelationIdFilter and
-- TrainEventMessage.newEvent). Without this column, an admin's HTTP
-- request and the Kafka event it produced could never be joined together
-- in the audit trail.

ALTER TABLE kafka_event_log ADD COLUMN correlation_id VARCHAR(64);

CREATE INDEX idx_kafka_event_log_correlation ON kafka_event_log(correlation_id);
