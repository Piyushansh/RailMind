-- RailMind core schema (demo/fictional railway data)

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    roles VARCHAR(200) NOT NULL DEFAULT 'USER',   -- comma separated: USER,ADMIN,SUPPORT_AGENT
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE stations (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE trains (
    id BIGSERIAL PRIMARY KEY,
    number VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    train_type VARCHAR(50) NOT NULL,   -- EXPRESS, SUPERFAST, RAJDHANI_STYLE, LOCAL
    source_station_id BIGINT NOT NULL REFERENCES stations(id),
    destination_station_id BIGINT NOT NULL REFERENCES stations(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE routes (
    id BIGSERIAL PRIMARY KEY,
    train_id BIGINT NOT NULL REFERENCES trains(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE route_stations (
    id BIGSERIAL PRIMARY KEY,
    route_id BIGINT NOT NULL REFERENCES routes(id),
    station_id BIGINT NOT NULL REFERENCES stations(id),
    sequence_no INT NOT NULL,
    arrival_offset_minutes INT NOT NULL,     -- minutes from journey start, NULL-equivalent 0 for origin
    departure_offset_minutes INT NOT NULL,
    distance_km INT NOT NULL,
    UNIQUE(route_id, sequence_no)
);

CREATE TABLE train_schedules (
    id BIGSERIAL PRIMARY KEY,
    train_id BIGINT NOT NULL REFERENCES trains(id),
    departure_date DATE NOT NULL,
    departure_time TIME NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',  -- SCHEDULED, DELAYED, CANCELLED, DEPARTED, ARRIVED
    delay_minutes INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(train_id, departure_date)
);

CREATE TABLE coaches (
    id BIGSERIAL PRIMARY KEY,
    train_id BIGINT NOT NULL REFERENCES trains(id),
    coach_code VARCHAR(10) NOT NULL,         -- e.g. S1, A1, B1
    travel_class VARCHAR(10) NOT NULL,       -- SL, AC3, AC2, AC1, CC
    total_seats INT NOT NULL,
    UNIQUE(train_id, coach_code)
);

CREATE TABLE seats (
    id BIGSERIAL PRIMARY KEY,
    coach_id BIGINT NOT NULL REFERENCES coaches(id),
    seat_number VARCHAR(10) NOT NULL,
    berth_type VARCHAR(15),                  -- LOWER, MIDDLE, UPPER, SIDE_LOWER, SIDE_UPPER, WINDOW, AISLE
    UNIQUE(coach_id, seat_number)
);

-- one row per (schedule, seat): tracks whether a specific seat on a specific date is booked
CREATE TABLE seat_availability (
    id BIGSERIAL PRIMARY KEY,
    schedule_id BIGINT NOT NULL REFERENCES train_schedules(id),
    seat_id BIGINT NOT NULL REFERENCES seats(id),
    status VARCHAR(15) NOT NULL DEFAULT 'AVAILABLE',  -- AVAILABLE, HELD, BOOKED
    version BIGINT NOT NULL DEFAULT 0,                 -- optimistic locking
    UNIQUE(schedule_id, seat_id)
);

CREATE TABLE fares (
    id BIGSERIAL PRIMARY KEY,
    train_id BIGINT NOT NULL REFERENCES trains(id),
    travel_class VARCHAR(10) NOT NULL,
    base_fare NUMERIC(10,2) NOT NULL,
    per_km_rate NUMERIC(10,4) NOT NULL DEFAULT 0,
    UNIQUE(train_id, travel_class)
);

CREATE TABLE bookings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    schedule_id BIGINT NOT NULL REFERENCES train_schedules(id),
    source_station_id BIGINT NOT NULL REFERENCES stations(id),
    destination_station_id BIGINT NOT NULL REFERENCES stations(id),
    travel_class VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT',  -- PENDING_PAYMENT, CONFIRMED, CANCELLED, EXPIRED
    total_fare NUMERIC(10,2) NOT NULL,
    idempotency_key VARCHAR(80) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE passengers (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    age INT NOT NULL,
    gender VARCHAR(10) NOT NULL
);

CREATE TABLE booking_passengers (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id),
    passenger_id BIGINT NOT NULL REFERENCES passengers(id),
    seat_id BIGINT REFERENCES seats(id),
    coach_id BIGINT REFERENCES coaches(id),
    berth_preference VARCHAR(15)
);

CREATE TABLE pnrs (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE REFERENCES bookings(id),
    pnr_number VARCHAR(15) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id),
    amount NUMERIC(10,2) NOT NULL,
    status VARCHAR(15) NOT NULL DEFAULT 'PENDING',   -- PENDING, SUCCESS, FAILED
    provider VARCHAR(30) NOT NULL DEFAULT 'MOCK',
    provider_reference VARCHAR(80),
    idempotency_key VARCHAR(80) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE cancellations (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id),
    cancelled_by_user_id BIGINT NOT NULL REFERENCES users(id),
    reason VARCHAR(255),
    hours_before_departure NUMERIC(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE refunds (
    id BIGSERIAL PRIMARY KEY,
    cancellation_id BIGINT NOT NULL UNIQUE REFERENCES cancellations(id),
    original_fare NUMERIC(10,2) NOT NULL,
    deduction_percent NUMERIC(5,2) NOT NULL,
    refund_amount NUMERIC(10,2) NOT NULL,
    status VARCHAR(15) NOT NULL DEFAULT 'PROCESSED',
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(60),
    entity_id BIGINT,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE ai_conversations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE ai_messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES ai_conversations(id),
    role VARCHAR(15) NOT NULL,        -- USER, ASSISTANT, TOOL
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE ai_tool_executions (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES ai_conversations(id),
    tool_name VARCHAR(80) NOT NULL,
    tool_input TEXT,
    tool_output TEXT,
    success BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_stations_city ON stations(city);
CREATE INDEX idx_schedules_train_date ON train_schedules(train_id, departure_date);
CREATE INDEX idx_seat_avail_schedule ON seat_availability(schedule_id, status);
CREATE INDEX idx_bookings_user ON bookings(user_id);
CREATE INDEX idx_bookings_schedule ON bookings(schedule_id);
CREATE INDEX idx_route_stations_route ON route_stations(route_id);
CREATE INDEX idx_audit_actor ON audit_logs(actor_user_id);
CREATE INDEX idx_ai_messages_conv ON ai_messages(conversation_id);
