-- Feature 016: removes everything the occupancy seed scripts created (all buildings named 'Seed 016 …').
-- Usage: docker exec -i raumlotse-postgres psql -U raumlotse -d raumlotse -v ON_ERROR_STOP=1 < specs/016-admin-occupancy-overview/seed/occupancy-seed-cleanup.sql
BEGIN;

CREATE TEMP TABLE seed_rooms ON COMMIT DROP AS
SELECT r.id
FROM room r
JOIN floor f ON f.id = r.floor_id
JOIN building b ON b.id = f.building_id
WHERE b.name LIKE 'Seed 016 %';

DELETE FROM booking_confirmation WHERE reservation_id IN (SELECT id FROM reservation WHERE room_id IN (SELECT id FROM seed_rooms));
DELETE FROM reservation WHERE room_id IN (SELECT id FROM seed_rooms);
DELETE FROM room WHERE id IN (SELECT id FROM seed_rooms);
DELETE FROM floor_map WHERE floor_id IN (SELECT f.id FROM floor f JOIN building b ON b.id = f.building_id WHERE b.name LIKE 'Seed 016 %');
DELETE FROM floor WHERE building_id IN (SELECT id FROM building WHERE name LIKE 'Seed 016 %');
DELETE FROM building WHERE name LIKE 'Seed 016 %';

COMMIT;
