-- Feature 017: test data for the manual quickstart scenarios 1–7, 9, 10 and 10a–10d.
-- Creates the lecturers X and Y, the equipment catalog entries and building 'Seed 017 Haus R' with rooms A–M.
-- Safe to rerun: it first removes 'Seed 017 Haus R' (rooms, bookings, favorites) and resets X and Y.
-- No bookings are created here, so the order in scenarios 1–7 is not influenced by popularity;
-- load ranking-seed-popularity.sql for scenario 8.
--
-- X and Y get the password of the oldest administrator account, normally the local fixture account
-- (AUTH_FIXTURE_EMAIL). Pass -v fixture_email=<e-mail> to copy the password of another account instead.
--   docker exec -i raumlotse-postgres psql -U raumlotse -d raumlotse -v ON_ERROR_STOP=1 < specs/017-room-search-ranking/seed/ranking-seed.sql
-- Logins: x@seed017.local and y@seed017.local, both role LECTURER.
BEGIN;

\if :{?fixture_email}
\else
-- Always one row, so a database without administrator reaches the readable error below.
SELECT coalesce((SELECT u.email
                 FROM user_account u JOIN role_assignment ra ON ra.user_id = u.id AND ra.role_code = 'ADMIN'
                 WHERE u.email NOT LIKE '%@seed017.local'
                 ORDER BY u.created_at LIMIT 1), '') AS fixture_email \gset
\endif
SET LOCAL seed.fixture_email = :'fixture_email';

-- Fail early if the fixture account does not exist (its password hash is copied below).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM user_account WHERE lower(email) = lower(current_setting('seed.fixture_email'))) THEN
        RAISE EXCEPTION 'No account "%" found – pass -v fixture_email=<e-mail> of the account whose password X and Y get',
            current_setting('seed.fixture_email');
    END IF;
END $$;

-- Cleanup of a previous run.
CREATE TEMP TABLE seed_rooms ON COMMIT DROP AS
SELECT r.id FROM room r JOIN floor f ON f.id = r.floor_id JOIN building b ON b.id = f.building_id
WHERE b.name = 'Seed 017 Haus R';
DELETE FROM booking_confirmation WHERE reservation_id IN (SELECT id FROM reservation WHERE room_id IN (SELECT id FROM seed_rooms));
DELETE FROM reservation WHERE room_id IN (SELECT id FROM seed_rooms);
DELETE FROM room WHERE id IN (SELECT id FROM seed_rooms);
DELETE FROM floor WHERE building_id IN (SELECT id FROM building WHERE name = 'Seed 017 Haus R');
DELETE FROM building WHERE name = 'Seed 017 Haus R';
DELETE FROM room_favorite WHERE user_id IN (SELECT id FROM user_account WHERE email IN ('x@seed017.local', 'y@seed017.local'));

-- Lecturers X and Y with the fixture account's password. The insert trigger gives them VIEWER; switch to LECTURER.
INSERT INTO user_account (email, display_name, password_hash)
SELECT v.email, v.display_name, f.password_hash
FROM (VALUES ('x@seed017.local', 'Seed 017 X'), ('y@seed017.local', 'Seed 017 Y')) AS v(email, display_name)
CROSS JOIN (SELECT password_hash FROM user_account WHERE lower(email) = lower(:'fixture_email')) f
WHERE NOT EXISTS (SELECT 1 FROM user_account u WHERE u.email = v.email);
DELETE FROM role_assignment
WHERE user_id IN (SELECT id FROM user_account WHERE email IN ('x@seed017.local', 'y@seed017.local'));
INSERT INTO role_assignment (user_id, role_code)
SELECT id, 'LECTURER' FROM user_account WHERE email IN ('x@seed017.local', 'y@seed017.local');

-- Equipment catalog: created if missing, classification always set (scenario 9 adds 'Beamer 4K' by hand).
INSERT INTO equipment_type (name, code)
SELECT v.name, v.code
FROM (VALUES ('Whiteboard', 'WHITEBOARD'), ('PC-Arbeitsplätze', 'PC_ARBEITSPLAETZE'),
             ('Klimaanlage', 'KLIMAANLAGE'), ('Steckdosen am Platz', 'STECKDOSEN_AM_PLATZ')) AS v(name, code)
WHERE NOT EXISTS (SELECT 1 FROM equipment_type e WHERE lower(e.name) = lower(v.name));
UPDATE equipment_type SET specialised = (lower(name) = lower('PC-Arbeitsplätze')), status = 'ACTIVE'
WHERE lower(name) IN ('whiteboard', lower('PC-Arbeitsplätze'), 'klimaanlage', 'steckdosen am platz');

-- Building 'Seed 017 Haus R', ground floor, rooms A–M with one seating arrangement each.
INSERT INTO building (name, has_elevator) VALUES ('Seed 017 Haus R', true);
INSERT INTO floor (building_id, name, ground_floor)
SELECT id, 'EG', true FROM building WHERE name = 'Seed 017 Haus R';
CREATE TEMP TABLE seed_layout (room_name TEXT, capacity INT, equipment TEXT[]) ON COMMIT DROP;
INSERT INTO seed_layout VALUES
    ('A', 6, ARRAY['Whiteboard']),
    ('B', 30, ARRAY['Whiteboard']),
    ('C', 100, ARRAY['Whiteboard']),
    ('F', 21, ARRAY['Whiteboard', 'PC-Arbeitsplätze']),
    ('G', 24, ARRAY['Whiteboard']),
    ('H', 45, ARRAY['Whiteboard']),
    ('J', 90, ARRAY['Whiteboard']),
    ('M', 24, ARRAY['Whiteboard', 'Klimaanlage', 'Steckdosen am Platz']);
INSERT INTO room (name, floor_id)
SELECT l.room_name, f.id FROM seed_layout l
CROSS JOIN floor f JOIN building b ON b.id = f.building_id WHERE b.name = 'Seed 017 Haus R';
INSERT INTO seating_arrangement (room_id, name, max_capacity)
SELECT r.id, 'Theater', l.capacity FROM seed_layout l
JOIN room r ON r.name = l.room_name
JOIN floor f ON f.id = r.floor_id JOIN building b ON b.id = f.building_id
WHERE b.name = 'Seed 017 Haus R';
INSERT INTO room_equipment (room_id, equipment_type_id)
SELECT r.id, e.id FROM seed_layout l
CROSS JOIN LATERAL unnest(l.equipment) AS eq(name)
JOIN equipment_type e ON lower(e.name) = lower(eq.name)
JOIN room r ON r.name = l.room_name
JOIN floor f ON f.id = r.floor_id JOIN building b ON b.id = f.building_id
WHERE b.name = 'Seed 017 Haus R';

COMMIT;
