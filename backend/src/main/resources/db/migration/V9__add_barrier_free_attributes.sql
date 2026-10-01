-- Feature 008: barrier-free reachability. Defaults of false never promise step-free access for rooms
-- whose data an administrator has not maintained yet.
ALTER TABLE building ADD COLUMN has_elevator BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE floor ADD COLUMN ground_floor BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE room ADD COLUMN not_barrier_free BOOLEAN NOT NULL DEFAULT false;
