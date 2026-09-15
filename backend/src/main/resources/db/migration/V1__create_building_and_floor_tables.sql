CREATE TABLE building (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE'
);

CREATE UNIQUE INDEX uk_building_name ON building (lower(name));

CREATE TABLE floor (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    building_id UUID NOT NULL REFERENCES building (id),
    name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE'
);

CREATE UNIQUE INDEX uk_floor_building_name ON floor (building_id, lower(name));
CREATE INDEX ix_floor_building_id ON floor (building_id);
