-- In-app notification history. Email/SMS are handled as abstractions in code
-- (NotificationService) with a mock sender for this scope - no external
-- delivery integration exists yet, only the in-app record and read/unread
-- state described here.

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    type VARCHAR(50) NOT NULL,          -- BOOKING_CONFIRMED, PAYMENT_FAILED, BOOKING_CANCELLED, REFUND_PROCESSED, ...
    title VARCHAR(150) NOT NULL,
    message VARCHAR(500) NOT NULL,
    related_booking_id BIGINT REFERENCES bookings(id),
    read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, read);
