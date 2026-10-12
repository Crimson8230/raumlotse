-- Feature 017: bookings for quickstart scenario 8 (popularity). Run after ranking-seed.sql.
-- G gets 3 completed bookings in the last 90 days plus 10 cancelled and 10 next week (neither counts);
-- M gets 12 completed bookings in the last 90 days. Safe to rerun: replaces the bookings of G and M.
--   docker exec -i raumlotse-postgres psql -U raumlotse -d raumlotse -v ON_ERROR_STOP=1 < specs/017-room-search-ranking/seed/ranking-seed-popularity.sql
BEGIN;

CREATE FUNCTION pg_temp.room(room_name TEXT) RETURNS UUID AS $$
    SELECT r.id FROM room r JOIN floor f ON f.id = r.floor_id JOIN building b ON b.id = f.building_id
    WHERE b.name = 'Seed 017 Haus R' AND r.name = room_name;
$$ LANGUAGE sql;

-- count bookings of one hour, one per day, starting first_day days from today (negative = past).
CREATE FUNCTION pg_temp.book(room_name TEXT, booking_count INT, first_day INT, booking_status TEXT) RETURNS VOID AS $$
    INSERT INTO reservation (room_id, seating_arrangement_id, start_time, end_time, status, expected_attendees,
                             created_by, reserved_for)
    SELECT s.room_id, s.id, date_trunc('hour', now()) + make_interval(days => first_day + i),
           date_trunc('hour', now()) + make_interval(days => first_day + i, hours => 1),
           booking_status, 20, 'seed@raumlotse.local', 'Seed 017'
    FROM seating_arrangement s CROSS JOIN generate_series(0, booking_count - 1) AS i
    WHERE s.room_id = pg_temp.room(room_name);
$$ LANGUAGE sql;

DO $$
BEGIN
    IF pg_temp.room('G') IS NULL OR pg_temp.room('M') IS NULL THEN
        RAISE EXCEPTION 'Rooms G/M not found – run ranking-seed.sql first';
    END IF;
END $$;

DELETE FROM booking_confirmation WHERE reservation_id IN
    (SELECT id FROM reservation WHERE room_id IN (pg_temp.room('G'), pg_temp.room('M')));
DELETE FROM reservation WHERE room_id IN (pg_temp.room('G'), pg_temp.room('M'));

DO $$
BEGIN
    PERFORM pg_temp.book('M', 12, -40, 'COMPLETED');
    PERFORM pg_temp.book('G', 3, -40, 'COMPLETED');
    PERFORM pg_temp.book('G', 10, -30, 'CANCELLED');
    PERFORM pg_temp.book('G', 10, 7, 'RESERVED');
END $$;

COMMIT;
