-- Feature 017: removes everything the ranking seed scripts created: all buildings named 'Seed 017 …' with their
-- rooms and bookings, and the accounts x@seed017.local / y@seed017.local with their favorites and roles.
-- The equipment types (Whiteboard, PC-Arbeitsplätze, Klimaanlage, Steckdosen am Platz) are kept, because other
-- rooms may use them; reclassify or delete them in "Stammdaten → Ausstattung" if needed.
--   docker exec -i raumlotse-postgres psql -U raumlotse -d raumlotse -v ON_ERROR_STOP=1 < specs/017-room-search-ranking/seed/ranking-seed-cleanup.sql
BEGIN;

CREATE TEMP TABLE seed_rooms ON COMMIT DROP AS
SELECT r.id FROM room r JOIN floor f ON f.id = r.floor_id JOIN building b ON b.id = f.building_id
WHERE b.name LIKE 'Seed 017 %';
DELETE FROM booking_confirmation WHERE reservation_id IN (SELECT id FROM reservation WHERE room_id IN (SELECT id FROM seed_rooms));
DELETE FROM reservation WHERE room_id IN (SELECT id FROM seed_rooms);
DELETE FROM room WHERE id IN (SELECT id FROM seed_rooms);
DELETE FROM floor WHERE building_id IN (SELECT id FROM building WHERE name LIKE 'Seed 017 %');
DELETE FROM building WHERE name LIKE 'Seed 017 %';

CREATE TEMP TABLE seed_users ON COMMIT DROP AS
SELECT id FROM user_account WHERE email IN ('x@seed017.local', 'y@seed017.local');
-- Bookings X or Y made by hand during the scenarios (in other buildings) must go before their accounts.
DELETE FROM booking_confirmation WHERE reservation_id IN
    (SELECT id FROM reservation WHERE created_by_user_id IN (SELECT id FROM seed_users));
DELETE FROM reservation WHERE created_by_user_id IN (SELECT id FROM seed_users);
DELETE FROM role_assignment WHERE user_id IN (SELECT id FROM seed_users);
DELETE FROM user_role_state WHERE user_id IN (SELECT id FROM seed_users);
DELETE FROM user_account WHERE id IN (SELECT id FROM seed_users);

COMMIT;
