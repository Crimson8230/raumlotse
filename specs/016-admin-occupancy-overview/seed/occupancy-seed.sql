-- Feature 016: test data for the manual quickstart scenarios 1–8 and 10.
-- Creates 'Seed 016 A' (R1 on EG, R2 on 1. OG) and 'Seed 016 B' (R3 on EG) with bookings relative to now.
-- Safe to rerun: it first removes the 'Seed 016 A/B' buildings and their bookings.
--
-- Usage (stack running via docker compose):
--   docker exec -i raumlotse-postgres psql -U raumlotse -d raumlotse -v ON_ERROR_STOP=1 \
--     < specs/016-admin-occupancy-overview/seed/occupancy-seed.sql
--
-- Note: the reservation sweep expires a RESERVED booking once its start is more than the grace period
-- (default 5 min, "Einstellungen" page) in the past. R2 below starts 2 min ago, so run scenarios 1–2 right after
-- seeding, or raise the grace period to 30 min first.
BEGIN;

-- Cleanup of a previous run (only the two scenario buildings).
CREATE TEMP TABLE seed_rooms ON COMMIT DROP AS
SELECT r.id FROM room r JOIN floor f ON f.id = r.floor_id JOIN building b ON b.id = f.building_id
WHERE b.name IN ('Seed 016 A', 'Seed 016 B');
DELETE FROM booking_confirmation WHERE reservation_id IN (SELECT id FROM reservation WHERE room_id IN (SELECT id FROM seed_rooms));
DELETE FROM reservation WHERE room_id IN (SELECT id FROM seed_rooms);
DELETE FROM room WHERE id IN (SELECT id FROM seed_rooms);
DELETE FROM floor_map WHERE floor_id IN (SELECT f.id FROM floor f JOIN building b ON b.id = f.building_id WHERE b.name IN ('Seed 016 A', 'Seed 016 B'));
DELETE FROM floor WHERE building_id IN (SELECT id FROM building WHERE name IN ('Seed 016 A', 'Seed 016 B'));
DELETE FROM building WHERE name IN ('Seed 016 A', 'Seed 016 B');

-- Local Berlin time on today + day_offset as timestamptz.
CREATE FUNCTION pg_temp.local_at(day_offset INT, hh INT, mm INT) RETURNS TIMESTAMPTZ AS $$
    SELECT (date_trunc('day', now() AT TIME ZONE 'Europe/Berlin')
            + make_interval(days => day_offset, hours => hh, mins => mm)) AT TIME ZONE 'Europe/Berlin';
$$ LANGUAGE sql;

CREATE FUNCTION pg_temp.room(building_name TEXT, floor_name TEXT, room_name TEXT) RETURNS UUID AS $$
    SELECT r.id FROM room r JOIN floor f ON f.id = r.floor_id JOIN building b ON b.id = f.building_id
    WHERE b.name = building_name AND f.name = floor_name AND r.name = room_name;
$$ LANGUAGE sql;

CREATE FUNCTION pg_temp.book(room_id UUID, start_at TIMESTAMPTZ, end_at TIMESTAMPTZ, booking_status TEXT,
        attendees INT, checked_in TIMESTAMPTZ) RETURNS VOID AS $$
    INSERT INTO reservation (room_id, seating_arrangement_id, start_time, end_time, status, expected_attendees,
                             created_by, reserved_for, check_in_method, checked_in_at)
    SELECT room_id, s.id, start_at, end_at, booking_status, attendees, 'seed@raumlotse.local', 'Seed 016',
           CASE WHEN checked_in IS NULL THEN NULL ELSE 'MANUAL' END, checked_in
    FROM seating_arrangement s WHERE s.room_id = book.room_id LIMIT 1;
$$ LANGUAGE sql;

-- Buildings, floors, rooms (each room gets one seating arrangement for 50 people).
INSERT INTO building (name, has_elevator) VALUES ('Seed 016 A', true), ('Seed 016 B', false);
INSERT INTO floor (building_id, name, ground_floor)
SELECT b.id, f.name, f.ground FROM building b
CROSS JOIN (VALUES ('EG', true), ('1. OG', false)) AS f(name, ground)
WHERE b.name = 'Seed 016 A'
UNION ALL
SELECT b.id, 'EG', true FROM building b WHERE b.name = 'Seed 016 B';
INSERT INTO room (name, floor_id)
SELECT x.room_name, f.id FROM floor f JOIN building b ON b.id = f.building_id
JOIN (VALUES ('Seed 016 A', 'EG', 'R1'), ('Seed 016 A', '1. OG', 'R2'), ('Seed 016 B', 'EG', 'R3'))
     AS x(building_name, floor_name, room_name) ON x.building_name = b.name AND x.floor_name = f.name;
INSERT INTO seating_arrangement (room_id, name, max_capacity)
SELECT r.id, 'Theater', 50 FROM room r JOIN floor f ON f.id = r.floor_id JOIN building b ON b.id = f.building_id
WHERE b.name IN ('Seed 016 A', 'Seed 016 B');

-- Scenario 1–3 and 10 (now): R1 checked in (4), R3 checked in (10), R2 started 2 min ago, not checked in (6).
SELECT pg_temp.book(pg_temp.room('Seed 016 A', 'EG', 'R1'), now() - interval '30 minutes', now() + interval '2 hours', 'ACTIVE', 4, now() - interval '30 minutes');
SELECT pg_temp.book(pg_temp.room('Seed 016 B', 'EG', 'R3'), now() - interval '30 minutes', now() + interval '2 hours', 'ACTIVE', 10, now() - interval '30 minutes');
SELECT pg_temp.book(pg_temp.room('Seed 016 A', '1. OG', 'R2'), now() - interval '2 minutes', now() + interval '2 hours', 'RESERVED', 6, NULL);

-- Scenario 4 and 5a (tomorrow): expected at 10:00 → A 17, B 7, total 24 in 3 rooms (peak); 11:00 → 17; 14:00 → 3.
SELECT pg_temp.book(pg_temp.room('Seed 016 A', 'EG', 'R1'), pg_temp.local_at(1, 10, 0), pg_temp.local_at(1, 12, 0), 'RESERVED', 5, NULL);
SELECT pg_temp.book(pg_temp.room('Seed 016 A', '1. OG', 'R2'), pg_temp.local_at(1, 10, 0), pg_temp.local_at(1, 12, 0), 'RESERVED', 12, NULL);
SELECT pg_temp.book(pg_temp.room('Seed 016 B', 'EG', 'R3'), pg_temp.local_at(1, 10, 0), pg_temp.local_at(1, 11, 0), 'RESERVED', 7, NULL);
SELECT pg_temp.book(pg_temp.room('Seed 016 A', 'EG', 'R1'), pg_temp.local_at(1, 14, 0), pg_temp.local_at(1, 15, 0), 'RESERVED', 3, NULL);
SELECT pg_temp.book(pg_temp.room('Seed 016 B', 'EG', 'R3'), pg_temp.local_at(1, 11, 0), pg_temp.local_at(1, 12, 0), 'CANCELLED', 20, NULL);

-- Scenario 5 (day after tomorrow): back-to-back in R1 → hour 10:00 shows 8 persons, 1 room, both bookings listed.
SELECT pg_temp.book(pg_temp.room('Seed 016 A', 'EG', 'R1'), pg_temp.local_at(2, 10, 0), pg_temp.local_at(2, 10, 30), 'RESERVED', 8, NULL);
SELECT pg_temp.book(pg_temp.room('Seed 016 A', 'EG', 'R1'), pg_temp.local_at(2, 10, 30), pg_temp.local_at(2, 11, 0), 'RESERVED', 6, NULL);

COMMIT;
