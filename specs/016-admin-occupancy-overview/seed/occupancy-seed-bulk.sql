-- Feature 016: load data for quickstart scenario 9 (SC-006): 100 rooms in 5 buildings and 500 running bookings,
-- plus 300 reservations tomorrow so the day forecast has a realistic shape.
-- Safe to rerun: it first removes the 'Seed 016 Perf …' buildings.
--
-- Usage (stack running via docker compose):
--   docker exec -i raumlotse-postgres psql -U raumlotse -d raumlotse -v ON_ERROR_STOP=1 \
--     < specs/016-admin-occupancy-overview/seed/occupancy-seed-bulk.sql
--
-- The running bookings are checked in (ACTIVE) from 30 min ago until 90 min from now; after that the sweep
-- completes them, so rerun the script before measuring. Five simultaneous bookings per room are not realistic
-- (normal booking prevents overlaps); they only exist to reach the volume of SC-006.
BEGIN;

CREATE TEMP TABLE seed_rooms ON COMMIT DROP AS
SELECT r.id FROM room r JOIN floor f ON f.id = r.floor_id JOIN building b ON b.id = f.building_id
WHERE b.name LIKE 'Seed 016 Perf %';
DELETE FROM booking_confirmation WHERE reservation_id IN (SELECT id FROM reservation WHERE room_id IN (SELECT id FROM seed_rooms));
DELETE FROM reservation WHERE room_id IN (SELECT id FROM seed_rooms);
DELETE FROM room WHERE id IN (SELECT id FROM seed_rooms);
DELETE FROM floor_map WHERE floor_id IN (SELECT f.id FROM floor f JOIN building b ON b.id = f.building_id WHERE b.name LIKE 'Seed 016 Perf %');
DELETE FROM floor WHERE building_id IN (SELECT id FROM building WHERE name LIKE 'Seed 016 Perf %');
DELETE FROM building WHERE name LIKE 'Seed 016 Perf %';

-- 5 buildings × 2 floors × 10 rooms = 100 rooms.
INSERT INTO building (name, has_elevator)
SELECT 'Seed 016 Perf ' || n, true FROM generate_series(1, 5) AS n;
INSERT INTO floor (building_id, name, ground_floor)
SELECT b.id, f.name, f.ground FROM building b
CROSS JOIN (VALUES ('EG', true), ('1. OG', false)) AS f(name, ground)
WHERE b.name LIKE 'Seed 016 Perf %';
INSERT INTO room (name, floor_id)
SELECT 'P' || substr(b.name, length(b.name)) || CASE WHEN f.ground_floor THEN '0' ELSE '1' END || lpad(n::text, 2, '0'), f.id
FROM floor f JOIN building b ON b.id = f.building_id
CROSS JOIN generate_series(1, 10) AS n
WHERE b.name LIKE 'Seed 016 Perf %';
INSERT INTO seating_arrangement (room_id, name, max_capacity)
SELECT r.id, 'Theater', 200 FROM room r JOIN floor f ON f.id = r.floor_id JOIN building b ON b.id = f.building_id
WHERE b.name LIKE 'Seed 016 Perf %';

-- 500 running, checked-in bookings (5 per room, 10 attendees each → 5000 checked-in persons in 100 rooms).
INSERT INTO reservation (room_id, seating_arrangement_id, start_time, end_time, status, expected_attendees,
                         created_by, reserved_for, check_in_method, checked_in_at)
SELECT r.id, s.id, now() - interval '30 minutes', now() + interval '90 minutes', 'ACTIVE', 10,
       'seed@raumlotse.local', 'Seed 016', 'MANUAL', now() - interval '30 minutes'
FROM room r
JOIN seating_arrangement s ON s.room_id = r.id
JOIN floor f ON f.id = r.floor_id JOIN building b ON b.id = f.building_id
CROSS JOIN generate_series(1, 5)
WHERE b.name LIKE 'Seed 016 Perf %';

-- Tomorrow: 3 non-overlapping reservations per room between 08:00 and 20:00 local time, 5–40 attendees.
INSERT INTO reservation (room_id, seating_arrangement_id, start_time, end_time, status, expected_attendees,
                         created_by, reserved_for)
SELECT r.id, s.id,
       (date_trunc('day', now() AT TIME ZONE 'Europe/Berlin') + interval '1 day'
           + make_interval(hours => 8 + slot * 4 + (row_number() OVER (PARTITION BY slot ORDER BY r.name))::int % 3))
           AT TIME ZONE 'Europe/Berlin',
       (date_trunc('day', now() AT TIME ZONE 'Europe/Berlin') + interval '1 day'
           + make_interval(hours => 9 + slot * 4 + (row_number() OVER (PARTITION BY slot ORDER BY r.name))::int % 3))
           AT TIME ZONE 'Europe/Berlin',
       'RESERVED', 5 + ((row_number() OVER (PARTITION BY slot ORDER BY r.name))::int * 7) % 36,
       'seed@raumlotse.local', 'Seed 016'
FROM room r
JOIN seating_arrangement s ON s.room_id = r.id
JOIN floor f ON f.id = r.floor_id JOIN building b ON b.id = f.building_id
CROSS JOIN generate_series(0, 2) AS slot
WHERE b.name LIKE 'Seed 016 Perf %';

COMMIT;
