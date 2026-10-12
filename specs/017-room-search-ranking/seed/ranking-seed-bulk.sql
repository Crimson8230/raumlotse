-- Feature 017: data volume for quickstart scenario 11 (SC-004): 500 rooms in 10 buildings 'Seed 017 Perf 1–10'
-- and 5 000 completed bookings spread over the last 89 days. Safe to rerun: replaces the 'Seed 017 Perf' data.
--   docker exec -i raumlotse-postgres psql -U raumlotse -d raumlotse -v ON_ERROR_STOP=1 < specs/017-room-search-ranking/seed/ranking-seed-bulk.sql
-- With this data loaded, searches without a building filter also list the Perf rooms; filter on
-- 'Seed 017 Haus R' for scenarios 1–10.
BEGIN;

CREATE TEMP TABLE seed_rooms ON COMMIT DROP AS
SELECT r.id FROM room r JOIN floor f ON f.id = r.floor_id JOIN building b ON b.id = f.building_id
WHERE b.name LIKE 'Seed 017 Perf %';
DELETE FROM booking_confirmation WHERE reservation_id IN (SELECT id FROM reservation WHERE room_id IN (SELECT id FROM seed_rooms));
DELETE FROM reservation WHERE room_id IN (SELECT id FROM seed_rooms);
DELETE FROM room WHERE id IN (SELECT id FROM seed_rooms);
DELETE FROM floor WHERE building_id IN (SELECT id FROM building WHERE name LIKE 'Seed 017 Perf %');
DELETE FROM building WHERE name LIKE 'Seed 017 Perf %';

INSERT INTO building (name, has_elevator)
SELECT 'Seed 017 Perf ' || b, b % 2 = 0 FROM generate_series(1, 10) AS b;
INSERT INTO floor (building_id, name, ground_floor)
SELECT id, 'EG', true FROM building WHERE name LIKE 'Seed 017 Perf %';
INSERT INTO room (name, floor_id)
SELECT 'Perf ' || lpad(r::text, 2, '0'), f.id
FROM floor f JOIN building b ON b.id = f.building_id CROSS JOIN generate_series(1, 50) AS r
WHERE b.name LIKE 'Seed 017 Perf %';
-- Capacities 10–129 and two arrangements per room, so size bands differ between rooms.
INSERT INTO seating_arrangement (room_id, name, max_capacity)
SELECT r.id, a.name, 10 + (abs(hashtext(r.id::text)) % 120) / a.divisor
FROM room r JOIN floor f ON f.id = r.floor_id JOIN building b ON b.id = f.building_id
CROSS JOIN (VALUES ('Theater', 1), ('U-Shape', 3)) AS a(name, divisor)
WHERE b.name LIKE 'Seed 017 Perf %';
-- Every third room has a whiteboard, every seventh PC workstations.
INSERT INTO room_equipment (room_id, equipment_type_id)
SELECT r.id, e.id
FROM (SELECT r.id, row_number() OVER (ORDER BY r.id) AS n
      FROM room r JOIN floor f ON f.id = r.floor_id JOIN building b ON b.id = f.building_id
      WHERE b.name LIKE 'Seed 017 Perf %') r
JOIN equipment_type e ON (lower(e.name) = 'whiteboard' AND r.n % 3 = 0)
                      OR (lower(e.name) = lower('PC-Arbeitsplätze') AND r.n % 7 = 0);

-- 5 000 completed one-hour bookings, 10 per room, spread over the last 89 days.
INSERT INTO reservation (room_id, seating_arrangement_id, start_time, end_time, status, expected_attendees,
                         created_by, reserved_for)
SELECT s.room_id, s.id, date_trunc('hour', now()) - make_interval(days => (1 + (i * 9 + n) % 89)::int),
       date_trunc('hour', now()) - make_interval(days => (1 + (i * 9 + n) % 89)::int) + interval '1 hour',
       'COMPLETED', 5, 'seed@raumlotse.local', 'Seed 017 Perf'
FROM (SELECT DISTINCT ON (s.room_id) s.room_id, s.id, row_number() OVER (ORDER BY s.room_id) AS n
      FROM seating_arrangement s JOIN room r ON r.id = s.room_id
      JOIN floor f ON f.id = r.floor_id JOIN building b ON b.id = f.building_id
      WHERE b.name LIKE 'Seed 017 Perf %' ORDER BY s.room_id, s.id) s
CROSS JOIN generate_series(0, 9) AS i;

COMMIT;
