-- Adds the three auth/profile capabilities the v6 baseline was missing:
--   1) password reset + email verification (hash-stored, single-use tokens -
--      same pattern as refresh_tokens: never store the raw token)
--   2) travel_preferences: one row per user, feeds both AI journey planning
--      (getUserPreferences tool) and normal search ranking defaults
--   3) saved passengers: passengers previously only existed booking-scoped
--      (no owner). owner_user_id lets a user build a reusable passenger list;
--      it stays NULL for one-off passengers entered directly during a booking.

CREATE TABLE password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens(user_id);

CREATE TABLE email_verification_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_email_verification_tokens_user ON email_verification_tokens(user_id);

CREATE TABLE travel_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    preferred_class VARCHAR(10),                 -- SL, AC3, AC2, AC1, CC
    preferred_berth VARCHAR(15),                 -- LOWER, MIDDLE, UPPER, SIDE_LOWER, SIDE_UPPER, WINDOW, AISLE
    budget_max NUMERIC(10,2),
    prefer_fastest BOOLEAN NOT NULL DEFAULT FALSE,
    prefer_cheapest BOOLEAN NOT NULL DEFAULT FALSE,
    prefer_overnight BOOLEAN NOT NULL DEFAULT FALSE,
    notify_email BOOLEAN NOT NULL DEFAULT TRUE,
    notify_delay BOOLEAN NOT NULL DEFAULT TRUE,
    notify_promotions BOOLEAN NOT NULL DEFAULT FALSE,
    trip_guardian_default_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

ALTER TABLE passengers ADD COLUMN owner_user_id BIGINT REFERENCES users(id);
ALTER TABLE passengers ADD COLUMN seat_preference VARCHAR(15);
ALTER TABLE passengers ADD COLUMN is_saved BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_passengers_owner ON passengers(owner_user_id) WHERE owner_user_id IS NOT NULL;
