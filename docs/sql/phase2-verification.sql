-- Phase 2 verification queries. Every query here returns 0 rows/0 count for
-- a healthy database unless explicitly noted otherwise (e.g. row-count
-- checks that should return a positive number). Run against a database that
-- has had V1-V13 applied in order.

-- ==== Demo data relationship checks (V11) ====

-- 1. Demo user exists
SELECT 'demo_user_exists' AS check_name, count(*) AS result FROM users WHERE email = 'user@railmind.demo';
-- expect: 1

-- 2. Bookings reference valid users/schedules/stations (no dangling FK - enforced by FK
--    constraints anyway, but confirms the seed actually populated them, not nulls where a
--    nullable column could have silently hidden a mistake)
SELECT 'bookings_missing_relations' AS check_name, count(*) AS result
FROM bookings b
WHERE b.user_id IS NULL OR b.schedule_id IS NULL OR b.source_station_id IS NULL OR b.destination_station_id IS NULL;
-- expect: 0

-- 3. Passengers/booking_passengers reference valid bookings and seats
SELECT 'booking_passengers_missing_relations' AS check_name, count(*) AS result
FROM booking_passengers bp
LEFT JOIN bookings b ON b.id = bp.booking_id
LEFT JOIN passengers p ON p.id = bp.passenger_id
LEFT JOIN seats s ON s.id = bp.seat_id
WHERE b.id IS NULL OR p.id IS NULL OR s.id IS NULL;
-- expect: 0

-- 4. PNR values are unique
SELECT 'duplicate_pnrs' AS check_name, count(*) AS result FROM (
    SELECT pnr_number FROM pnrs GROUP BY pnr_number HAVING count(*) > 1
) dup;
-- expect: 0

-- 5. Confirmed bookings have a SUCCESS payment
SELECT 'confirmed_bookings_without_success_payment' AS check_name, count(*) AS result
FROM bookings b
WHERE b.status = 'CONFIRMED'
  AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.booking_id = b.id AND p.status = 'SUCCESS');
-- expect: 0

-- 6. Cancelled seed booking has a matching cancellation + refund record
SELECT 'cancelled_seed_booking_has_cancellation_and_refund' AS check_name, count(*) AS result
FROM bookings b
JOIN cancellations c ON c.booking_id = b.id
JOIN refunds r ON r.cancellation_id = c.id
WHERE b.idempotency_key = 'seed-booking-3' AND b.status = 'CANCELLED' AND r.status = 'PROCESSED';
-- expect: 1

-- 7. Cancelled booking's seat is AVAILABLE (never held/booked for a cancelled seed booking)
SELECT 'cancelled_booking_seat_not_available' AS check_name, count(*) AS result
FROM bookings b
JOIN booking_passengers bp ON bp.booking_id = b.id
JOIN seat_availability sa ON sa.seat_id = bp.seat_id AND sa.schedule_id = b.schedule_id
WHERE b.idempotency_key = 'seed-booking-3' AND sa.status != 'AVAILABLE';
-- expect: 0

-- 8. Confirmed bookings' seats are BOOKED
SELECT 'confirmed_booking_seat_not_booked' AS check_name, count(*) AS result
FROM bookings b
JOIN booking_passengers bp ON bp.booking_id = b.id
JOIN seat_availability sa ON sa.seat_id = bp.seat_id AND sa.schedule_id = b.schedule_id
WHERE b.idempotency_key IN ('seed-booking-1', 'seed-booking-2') AND sa.status != 'BOOKED';
-- expect: 0

-- 9. Trip Guardian / disruption seed data valid: booking 2 has trip_guardian_enabled=true and an alert
SELECT 'trip_guardian_seed_valid' AS check_name, count(*) AS result
FROM bookings b
JOIN trip_alerts ta ON ta.booking_id = b.id
WHERE b.idempotency_key = 'seed-booking-2' AND b.trip_guardian_enabled = true AND ta.alert_type = 'DELAY_DETECTED';
-- expect: 1

-- 10. Notifications reference valid users and (where set) valid bookings
SELECT 'notifications_missing_relations' AS check_name, count(*) AS result
FROM notifications n
LEFT JOIN users u ON u.id = n.user_id
LEFT JOIN bookings b ON b.id = n.related_booking_id
WHERE u.id IS NULL OR (n.related_booking_id IS NOT NULL AND b.id IS NULL);
-- expect: 0

-- 11. No duplicate seed bookings (idempotency_key uniqueness for the three seed rows specifically)
SELECT 'duplicate_seed_bookings' AS check_name, count(*) AS result FROM (
    SELECT idempotency_key FROM bookings
    WHERE idempotency_key IN ('seed-booking-1', 'seed-booking-2', 'seed-booking-3')
    GROUP BY idempotency_key HAVING count(*) > 1
) dup;
-- expect: 0

-- ==== General database integrity checks (Task 7) ====

SELECT 'duplicate_train_numbers' AS check_name, count(*) AS result FROM (
    SELECT number FROM trains GROUP BY number HAVING count(*) > 1) d;
SELECT 'duplicate_station_codes' AS check_name, count(*) AS result FROM (
    SELECT code FROM stations GROUP BY code HAVING count(*) > 1) d;
SELECT 'duplicate_event_ids' AS check_name, count(*) AS result FROM (
    SELECT event_id FROM kafka_event_log GROUP BY event_id HAVING count(*) > 1) d;

SELECT 'orphan_passengers' AS check_name, count(*) AS result
FROM passengers p WHERE NOT EXISTS (SELECT 1 FROM booking_passengers bp WHERE bp.passenger_id = p.id)
  AND p.is_saved = false; -- unsaved passengers should always be attached to a booking; saved/reusable ones legitimately may not be

SELECT 'orphan_payments' AS check_name, count(*) AS result
FROM payments pay WHERE NOT EXISTS (SELECT 1 FROM bookings b WHERE b.id = pay.booking_id);

SELECT 'orphan_refunds' AS check_name, count(*) AS result
FROM refunds r WHERE NOT EXISTS (SELECT 1 FROM cancellations c WHERE c.id = r.cancellation_id);

SELECT 'orphan_notifications' AS check_name, count(*) AS result
FROM notifications n WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = n.user_id);

SELECT 'orphan_trip_alerts' AS check_name, count(*) AS result
FROM trip_alerts ta WHERE NOT EXISTS (SELECT 1 FROM bookings b WHERE b.id = ta.booking_id);

SELECT 'invalid_seat_status' AS check_name, count(*) AS result
FROM seat_availability WHERE status NOT IN ('AVAILABLE', 'HELD', 'BOOKED');

SELECT 'refunded_booking_without_cancellation' AS check_name, count(*) AS result
FROM refunds r WHERE NOT EXISTS (SELECT 1 FROM cancellations c WHERE c.id = r.cancellation_id);

SELECT 'cancelled_booking_with_booked_seats' AS check_name, count(*) AS result
FROM bookings b
JOIN booking_passengers bp ON bp.booking_id = b.id
JOIN seat_availability sa ON sa.seat_id = bp.seat_id AND sa.schedule_id = b.schedule_id
WHERE b.status = 'CANCELLED' AND sa.status = 'BOOKED';

SELECT 'null_required_fields_bookings' AS check_name, count(*) AS result
FROM bookings WHERE user_id IS NULL OR schedule_id IS NULL OR status IS NULL OR total_fare IS NULL;

-- Row-count sanity checks (should be positive, not zero, per the "at least N" seed requirements)
SELECT 'station_count' AS check_name, count(*) AS result FROM stations;         -- expect >= 15
SELECT 'train_count' AS check_name, count(*) AS result FROM trains;            -- expect >= 10
SELECT 'route_count' AS check_name, count(*) AS result FROM routes;            -- expect > 0
SELECT 'schedule_count' AS check_name, count(*) AS result FROM train_schedules; -- expect > 0
SELECT 'coach_count' AS check_name, count(*) AS result FROM coaches;           -- expect > 0
SELECT 'fare_count' AS check_name, count(*) AS result FROM fares;              -- expect > 0
