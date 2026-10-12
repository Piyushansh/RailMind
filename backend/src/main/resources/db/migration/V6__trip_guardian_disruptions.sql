-- Trip Guardian + disruption workflow support. Platform tracking is added to
-- train_schedules (previously untracked); Trip Guardian opt-in is a per-booking
-- flag rather than a separate subscription table since one booking = one journey
-- to monitor in this schema, which keeps ownership trivial (same FK as everything
-- else on bookings). trip_alerts is the timeline Trip Guardian/disruption events
-- render from. rebooked_from_booking_id links a disruption-triggered rebooking
-- back to the original cancelled booking for traceability.

ALTER TABLE train_schedules ADD COLUMN platform VARCHAR(10);

ALTER TABLE bookings ADD COLUMN trip_guardian_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE bookings ADD COLUMN rebooked_from_booking_id BIGINT REFERENCES bookings(id);

CREATE TABLE trip_alerts (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id),
    alert_type VARCHAR(50) NOT NULL,  -- DELAY_DETECTED, CANCELLATION_DETECTED, PLATFORM_CHANGED, SCHEDULE_CHANGED, ALTERNATIVE_RECOMMENDED, REBOOKING_APPROVED, REBOOKING_COMPLETED
    message VARCHAR(500) NOT NULL,
    suggested_alternative_schedule_id BIGINT REFERENCES train_schedules(id),
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_trip_alerts_booking ON trip_alerts(booking_id, created_at DESC);
CREATE INDEX idx_bookings_schedule_status ON bookings(schedule_id, status);
CREATE INDEX idx_bookings_trip_guardian ON bookings(trip_guardian_enabled) WHERE trip_guardian_enabled = TRUE;
