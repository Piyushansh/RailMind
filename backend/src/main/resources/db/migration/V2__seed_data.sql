-- Demo/fictional seed data for RailMind. Train numbers/names are fictional.

INSERT INTO stations (code, name, city, state) VALUES
 ('NDLS','RailMind Central','Delhi','Delhi'),
 ('BCT','Coastal Terminus','Mumbai','Maharashtra'),
 ('MAS','Southern Central','Chennai','Tamil Nadu'),
 ('HWH','Eastern Gateway','Kolkata','West Bengal'),
 ('SBC','Garden City Junction','Bengaluru','Karnataka'),
 ('PUNE','Deccan Junction','Pune','Maharashtra'),
 ('JAI','Pink City Station','Jaipur','Rajasthan'),
 ('LKO','Awadh Junction','Lucknow','Uttar Pradesh'),
 ('ADI','Riverside Terminus','Ahmedabad','Gujarat'),
 ('PAT','Ganga Junction','Patna','Bihar'),
 ('BBS','Temple City Station','Bhubaneswar','Odisha'),
 ('NGP','Orange City Junction','Nagpur','Maharashtra'),
 ('BPL','Central Highlands Jn','Bhopal','Madhya Pradesh'),
 ('GHY','Northeast Gateway','Guwahati','Assam'),
 ('CBE','Textile City Station','Coimbatore','Tamil Nadu');

INSERT INTO trains (number, name, train_type, source_station_id, destination_station_id) VALUES
 ('RM101','RailMind Express', 'SUPERFAST', (SELECT id FROM stations WHERE code='NDLS'), (SELECT id FROM stations WHERE code='BCT')),
 ('RM102','RailMind Express Return', 'SUPERFAST', (SELECT id FROM stations WHERE code='BCT'), (SELECT id FROM stations WHERE code='NDLS')),
 ('RM201','Coastal Rajmarg', 'RAJDHANI_STYLE', (SELECT id FROM stations WHERE code='NDLS'), (SELECT id FROM stations WHERE code='MAS')),
 ('RM202','Coastal Rajmarg Return', 'RAJDHANI_STYLE', (SELECT id FROM stations WHERE code='MAS'), (SELECT id FROM stations WHERE code='NDLS')),
 ('RM301','Garden City Flyer', 'SUPERFAST', (SELECT id FROM stations WHERE code='SBC'), (SELECT id FROM stations WHERE code='HWH')),
 ('RM302','Garden City Flyer Return', 'SUPERFAST', (SELECT id FROM stations WHERE code='HWH'), (SELECT id FROM stations WHERE code='SBC')),
 ('RM401','Deccan Runner', 'EXPRESS', (SELECT id FROM stations WHERE code='PUNE'), (SELECT id FROM stations WHERE code='ADI')),
 ('RM402','Deccan Runner Return', 'EXPRESS', (SELECT id FROM stations WHERE code='ADI'), (SELECT id FROM stations WHERE code='PUNE')),
 ('RM501','Awadh Superfast', 'SUPERFAST', (SELECT id FROM stations WHERE code='LKO'), (SELECT id FROM stations WHERE code='JAI')),
 ('RM601','Northeast Voyager', 'EXPRESS', (SELECT id FROM stations WHERE code='PAT'), (SELECT id FROM stations WHERE code='GHY'));

-- routes: one route per train, with 3-5 intermediate stops built from arbitrary demo stations
INSERT INTO routes (train_id) SELECT id FROM trains;

-- route_stations: origin + one midpoint + destination, offsets in minutes, distance in km
INSERT INTO route_stations (route_id, station_id, sequence_no, arrival_offset_minutes, departure_offset_minutes, distance_km)
SELECT r.id, t.source_station_id, 1, 0, 0, 0
FROM routes r JOIN trains t ON t.id = r.train_id;

INSERT INTO route_stations (route_id, station_id, sequence_no, arrival_offset_minutes, departure_offset_minutes, distance_km)
SELECT r.id,
       (SELECT id FROM stations WHERE code = 'BPL'),
       2, 360, 375, 700
FROM routes r
WHERE r.id NOT IN (SELECT route_id FROM route_stations WHERE station_id = (SELECT id FROM stations WHERE code='BPL'));

INSERT INTO route_stations (route_id, station_id, sequence_no, arrival_offset_minutes, departure_offset_minutes, distance_km)
SELECT r.id, t.destination_station_id, 3, 900, 900, 1400
FROM routes r JOIN trains t ON t.id = r.train_id;

-- coaches: 2 sleeper, 2 AC3, 1 AC2, 1 AC1 per train
INSERT INTO coaches (train_id, coach_code, travel_class, total_seats)
SELECT id, 'S1', 'SL', 72 FROM trains
UNION ALL SELECT id, 'S2', 'SL', 72 FROM trains
UNION ALL SELECT id, 'B1', 'AC3', 64 FROM trains
UNION ALL SELECT id, 'B2', 'AC3', 64 FROM trains
UNION ALL SELECT id, 'A1', 'AC2', 46 FROM trains
UNION ALL SELECT id, 'H1', 'AC1', 24 FROM trains;

-- seats: generate seat numbers per coach based on total_seats, cycling berth types
INSERT INTO seats (coach_id, seat_number, berth_type)
SELECT c.id,
       n::text,
       (ARRAY['LOWER','MIDDLE','UPPER','SIDE_LOWER','SIDE_UPPER'])[((n - 1) % 5) + 1]
FROM coaches c
JOIN LATERAL generate_series(1, c.total_seats) AS n ON true;

-- fares per train/class
INSERT INTO fares (train_id, travel_class, base_fare, per_km_rate)
SELECT id, 'SL', 150, 0.55 FROM trains
UNION ALL SELECT id, 'AC3', 450, 1.35 FROM trains
UNION ALL SELECT id, 'AC2', 750, 2.05 FROM trains
UNION ALL SELECT id, 'AC1', 1400, 3.40 FROM trains;

-- schedules: each train runs daily for the next 30 days, departure time derived from train id for variety
INSERT INTO train_schedules (train_id, departure_date, departure_time, status, delay_minutes)
SELECT t.id,
       (CURRENT_DATE + d.day_offset),
       (TIME '05:00' + ((t.id * 37) % 1080) * INTERVAL '1 minute'),
       'SCHEDULED',
       0
FROM trains t
JOIN LATERAL generate_series(0, 29) AS d(day_offset) ON true;

-- seat_availability: one row per (schedule, seat), all AVAILABLE initially
INSERT INTO seat_availability (schedule_id, seat_id, status, version)
SELECT ts.id, s.id, 'AVAILABLE', 0
FROM train_schedules ts
JOIN coaches c ON c.train_id = ts.train_id
JOIN seats s ON s.coach_id = c.id;

-- demo accounts (password for all: "Password123!" — this is a real bcrypt hash of that string)
INSERT INTO users (full_name, email, password_hash, roles, email_verified, enabled) VALUES
 ('Demo User', 'user@railmind.demo', '$2b$10$.QYS9bl9V/fPL4yyPIi/vOeE2qsYB/wv3nyWQ4ZROrplp9XS9mGKy', 'USER', true, true),
 ('Demo Admin', 'admin@railmind.demo', '$2b$10$.QYS9bl9V/fPL4yyPIi/vOeE2qsYB/wv3nyWQ4ZROrplp9XS9mGKy', 'USER,ADMIN', true, true),
 ('Demo Support Agent', 'support@railmind.demo', '$2b$10$.QYS9bl9V/fPL4yyPIi/vOeE2qsYB/wv3nyWQ4ZROrplp9XS9mGKy', 'USER,SUPPORT_AGENT', true, true);
