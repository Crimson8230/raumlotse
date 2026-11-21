-- Feature 024: floors of a building have an order (0 = ground floor, increasing upward).
ALTER TABLE floor ADD COLUMN position INT NOT NULL DEFAULT 0;

UPDATE floor f
SET position = ranked.rn - 1
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY building_id ORDER BY ground_floor DESC, lower(name), id) AS rn
    FROM floor
) ranked
WHERE f.id = ranked.id;

-- The ground-floor flag is now derived from the order and kept in sync by the application.
UPDATE floor SET ground_floor = (position = 0);
