-- Adds a hold-expiry timestamp to bookings so seats held during PENDING_PAYMENT
-- are guaranteed to be released even if the user abandons payment. A scheduled
-- job (SeatHoldExpiryService) sweeps for bookings past this timestamp.

ALTER TABLE bookings ADD COLUMN hold_expires_at TIMESTAMP;

-- Existing PENDING_PAYMENT rows (if any) from before this column existed get a
-- short grace window rather than an immediate expiry on next sweep.
UPDATE bookings SET hold_expires_at = created_at + INTERVAL '15 minutes'
WHERE status = 'PENDING_PAYMENT' AND hold_expires_at IS NULL;

CREATE INDEX idx_bookings_hold_expiry ON bookings(status, hold_expires_at);
