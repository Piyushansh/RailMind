-- Demo bookings/disruption/notifications for the pre-existing demo account
-- (user@railmind.demo, seeded in V2__seed_data.sql). V2 already seeds
-- stations/trains/routes/schedules/coaches/seats/fares/users but has zero
-- bookings, trip_alerts, or notifications - so the demo account had nothing
-- to look at on first login. This fills that in with three realistic
-- bookings covering the states the frontend/AI assistant actually need to
-- demo against: a plain confirmed booking, a confirmed booking with Trip
-- Guardian enabled plus an active disruption alert (the flagship demo
-- scenario), and a cancelled-with-refund booking.
--
-- Uses subqueries/CTEs throughout instead of hardcoded IDs, matching
-- V2__seed_data.sql's own style, so this runs correctly regardless of the
-- exact auto-generated IDs V1-V9 produced.

-- ---------------------------------------------------------------------
-- Booking 1: plain confirmed booking, RM101 (NDLS -> BCT), SL, 1 passenger
-- ---------------------------------------------------------------------
WITH sched AS (
    SELECT ts.id AS schedule_id, ts.train_id
    FROM train_schedules ts JOIN trains t ON t.id = ts.train_id
    WHERE t.number = 'RM101' AND ts.departure_date = CURRENT_DATE + 5
),
seat_pick AS (
    SELECT s.id AS seat_id, c.id AS coach_id
    FROM seats s JOIN coaches c ON c.id = s.coach_id JOIN sched ON c.train_id = sched.train_id
    WHERE c.travel_class = 'SL'
    ORDER BY s.id LIMIT 1
),
demo_user AS (SELECT id FROM users WHERE email = 'user@railmind.demo'),
new_passenger AS (
    INSERT INTO passengers (full_name, age, gender) VALUES ('Aarav Sharma', 29, 'M') RETURNING id
),
new_booking AS (
    INSERT INTO bookings (user_id, schedule_id, source_station_id, destination_station_id,
                           travel_class, status, total_fare, idempotency_key)
    SELECT demo_user.id, sched.schedule_id,
           (SELECT id FROM stations WHERE code = 'NDLS'), (SELECT id FROM stations WHERE code = 'BCT'),
           'SL', 'CONFIRMED', 575.00, 'seed-booking-1'
    FROM demo_user, sched
    RETURNING id
),
new_bp AS (
    INSERT INTO booking_passengers (booking_id, passenger_id, seat_id, coach_id, berth_preference)
    SELECT new_booking.id, new_passenger.id, seat_pick.seat_id, seat_pick.coach_id, 'LOWER'
    FROM new_booking, new_passenger, seat_pick
    RETURNING booking_id
),
mark_seat AS (
    UPDATE seat_availability sa SET status = 'BOOKED'
    FROM seat_pick, sched
    WHERE sa.schedule_id = sched.schedule_id AND sa.seat_id = seat_pick.seat_id
    RETURNING sa.id
),
new_pnr AS (
    INSERT INTO pnrs (booking_id, pnr_number) SELECT id, '4501234567' FROM new_booking RETURNING booking_id
)
INSERT INTO payments (booking_id, amount, status, provider, provider_reference, idempotency_key)
SELECT id, 575.00, 'SUCCESS', 'MOCK', 'seed-pay-ref-1', 'seed-pay-1' FROM new_booking;

-- ---------------------------------------------------------------------
-- Booking 2: confirmed, Trip Guardian ENABLED, RM201 (NDLS -> MAS), AC3,
-- 2 passengers, plus an active DELAY_DETECTED alert - this is the booking
-- the flagship demo scenario (spec section 72) points at.
-- ---------------------------------------------------------------------
WITH sched AS (
    SELECT ts.id AS schedule_id, ts.train_id
    FROM train_schedules ts JOIN trains t ON t.id = ts.train_id
    WHERE t.number = 'RM201' AND ts.departure_date = CURRENT_DATE + 10
),
seats_pick AS (
    SELECT s.id AS seat_id, c.id AS coach_id,
           row_number() OVER (ORDER BY s.id) AS rn
    FROM seats s JOIN coaches c ON c.id = s.coach_id JOIN sched ON c.train_id = sched.train_id
    WHERE c.travel_class = 'AC3'
    ORDER BY s.id LIMIT 2
),
demo_user AS (SELECT id FROM users WHERE email = 'user@railmind.demo'),
passenger_1 AS (INSERT INTO passengers (full_name, age, gender) VALUES ('Priya Nair', 34, 'F') RETURNING id),
passenger_2 AS (INSERT INTO passengers (full_name, age, gender) VALUES ('Kiran Nair', 8, 'M') RETURNING id),
new_booking AS (
    INSERT INTO bookings (user_id, schedule_id, source_station_id, destination_station_id,
                           travel_class, status, total_fare, idempotency_key, trip_guardian_enabled)
    SELECT demo_user.id, sched.schedule_id,
           (SELECT id FROM stations WHERE code = 'NDLS'), (SELECT id FROM stations WHERE code = 'MAS'),
           'AC3', 'CONFIRMED', 3400.00, 'seed-booking-2', TRUE
    FROM demo_user, sched
    RETURNING id
),
new_bp_1 AS (
    INSERT INTO booking_passengers (booking_id, passenger_id, seat_id, coach_id, berth_preference)
    SELECT new_booking.id, passenger_1.id, sp.seat_id, sp.coach_id, 'LOWER'
    FROM new_booking, passenger_1, seats_pick sp WHERE sp.rn = 1
    RETURNING booking_id
),
new_bp_2 AS (
    INSERT INTO booking_passengers (booking_id, passenger_id, seat_id, coach_id, berth_preference)
    SELECT new_booking.id, passenger_2.id, sp.seat_id, sp.coach_id, 'UPPER'
    FROM new_booking, passenger_2, seats_pick sp WHERE sp.rn = 2
    RETURNING booking_id
),
mark_seats AS (
    UPDATE seat_availability sa SET status = 'BOOKED'
    FROM seats_pick sp, sched
    WHERE sa.schedule_id = sched.schedule_id AND sa.seat_id = sp.seat_id
    RETURNING sa.id
),
new_pnr AS (
    INSERT INTO pnrs (booking_id, pnr_number) SELECT id, '4509876543' FROM new_booking RETURNING booking_id
),
new_payment AS (
    INSERT INTO payments (booking_id, amount, status, provider, provider_reference, idempotency_key)
    SELECT id, 3400.00, 'SUCCESS', 'MOCK', 'seed-pay-ref-2', 'seed-pay-2' FROM new_booking
    RETURNING booking_id
),
new_alert AS (
    INSERT INTO trip_alerts (booking_id, alert_type, message, resolved)
    SELECT id, 'DELAY_DETECTED',
           'Your train RM201 (Coastal Rajmarg) is running approximately 45 minutes late. We are monitoring alternatives for you.',
           FALSE
    FROM new_booking
    RETURNING booking_id
)
INSERT INTO notifications (user_id, type, title, message, related_booking_id)
SELECT demo_user.id, 'BOOKING_CONFIRMED', 'Booking confirmed',
       'Your booking on RM201 (Coastal Rajmarg) is confirmed. PNR 4509876543.', new_booking.id
FROM demo_user, new_booking;

-- Second notification for booking 2 (the delay alert itself), inserted separately since a single
-- INSERT statement can only target one table even with preceding CTEs. Guarded with NOT EXISTS:
-- unlike the booking/payment/cancellation inserts above (protected by real unique constraints
-- that make a genuine re-run fail loudly rather than duplicate), a bare INSERT ... SELECT has no
-- such constraint to lean on, so this guard is what keeps a forced re-run of this file safe.
INSERT INTO notifications (user_id, type, title, message, related_booking_id)
SELECT b.user_id, 'TRIP_DISRUPTION', 'Possible delay on your journey',
       'RM201 is running about 45 minutes late. Trip Guardian is monitoring alternatives for you.', b.id
FROM bookings b
WHERE b.idempotency_key = 'seed-booking-2'
  AND NOT EXISTS (
      SELECT 1 FROM notifications n WHERE n.related_booking_id = b.id AND n.type = 'TRIP_DISRUPTION'
  );

-- ---------------------------------------------------------------------
-- Booking 3: CANCELLED with refund, RM301 (SBC -> HWH), AC2, 1 passenger
-- ---------------------------------------------------------------------
WITH sched AS (
    SELECT ts.id AS schedule_id, ts.train_id
    FROM train_schedules ts JOIN trains t ON t.id = ts.train_id
    WHERE t.number = 'RM301' AND ts.departure_date = CURRENT_DATE + 3
),
seat_pick AS (
    SELECT s.id AS seat_id, c.id AS coach_id
    FROM seats s JOIN coaches c ON c.id = s.coach_id JOIN sched ON c.train_id = sched.train_id
    WHERE c.travel_class = 'AC2'
    ORDER BY s.id LIMIT 1
),
demo_user AS (SELECT id FROM users WHERE email = 'user@railmind.demo'),
new_passenger AS (INSERT INTO passengers (full_name, age, gender) VALUES ('Rohan Mehta', 41, 'M') RETURNING id),
new_booking AS (
    INSERT INTO bookings (user_id, schedule_id, source_station_id, destination_station_id,
                           travel_class, status, total_fare, idempotency_key)
    SELECT demo_user.id, sched.schedule_id,
           (SELECT id FROM stations WHERE code = 'SBC'), (SELECT id FROM stations WHERE code = 'HWH'),
           'AC2', 'CANCELLED', 2150.00, 'seed-booking-3'
    FROM demo_user, sched
    RETURNING id
),
new_bp AS (
    -- Seat is intentionally left AVAILABLE (not marked BOOKED/HELD): the booking is already
    -- CANCELLED, so the seat this cancelled booking used to occupy is correctly free again -
    -- there is nothing to release since it was never held in seat_availability for this seed.
    INSERT INTO booking_passengers (booking_id, passenger_id, seat_id, coach_id, berth_preference)
    SELECT new_booking.id, new_passenger.id, seat_pick.seat_id, seat_pick.coach_id, 'LOWER'
    FROM new_booking, new_passenger, seat_pick
    RETURNING booking_id
),
new_cancellation AS (
    INSERT INTO cancellations (booking_id, cancelled_by_user_id, reason, hours_before_departure)
    SELECT new_booking.id, demo_user.id, 'Change of plans', 60.0
    FROM new_booking, demo_user
    RETURNING id, booking_id
)
INSERT INTO refunds (cancellation_id, original_fare, deduction_percent, refund_amount, status)
SELECT id, 2150.00, 0.00, 2150.00, 'PROCESSED' FROM new_cancellation;

INSERT INTO notifications (user_id, type, title, message, related_booking_id)
SELECT b.user_id, 'BOOKING_CANCELLED', 'Booking cancelled',
       'Booking on RM301 (Garden City Flyer) was cancelled. A full refund of 2150.00 has been processed.', b.id
FROM bookings b
WHERE b.idempotency_key = 'seed-booking-3'
  AND NOT EXISTS (
      SELECT 1 FROM notifications n WHERE n.related_booking_id = b.id AND n.type = 'BOOKING_CANCELLED'
  );
