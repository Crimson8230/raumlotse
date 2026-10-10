CREATE TABLE role_permission_state (
    role_code VARCHAR(32) PRIMARY KEY
        CHECK (role_code IN ('ADMIN','UNIVERSITY_STAFF','STUDENT','LECTURER','VIEWER')),
    permissions_version BIGINT NOT NULL DEFAULT 0 CHECK (permissions_version >= 0)
);

CREATE TABLE role_permission (
    role_code VARCHAR(32) NOT NULL REFERENCES role_permission_state(role_code),
    permission_code VARCHAR(40) NOT NULL CHECK (permission_code IN (
        'READ', 'RESERVE', 'OWN_RESERVATION_MANAGE', 'OTHER_RESERVATION_MANAGE',
        'OWN_ACTIVE_DEVICE_CONTROL', 'BUILDING_MANAGE', 'FLOOR_MANAGE',
        'EQUIPMENT_TYPE_MANAGE', 'ROOM_MANAGE', 'MAP_MANAGE',
        'ROOM_PLACEMENT_MANAGE', 'CONNECTION_MANAGE', 'STATISTICS_READ',
        'RESERVATION_MAINTENANCE'
    )),
    PRIMARY KEY (role_code, permission_code)
);

INSERT INTO role_permission_state(role_code) VALUES
    ('ADMIN'), ('UNIVERSITY_STAFF'), ('STUDENT'), ('LECTURER'), ('VIEWER');

INSERT INTO role_permission(role_code, permission_code)
SELECT s.role_code, p.permission_code
FROM role_permission_state s
CROSS JOIN (VALUES
    ('READ'), ('RESERVE'), ('OWN_RESERVATION_MANAGE'),
    ('OTHER_RESERVATION_MANAGE'), ('OWN_ACTIVE_DEVICE_CONTROL'),
    ('BUILDING_MANAGE'), ('FLOOR_MANAGE'), ('EQUIPMENT_TYPE_MANAGE'),
    ('ROOM_MANAGE'), ('MAP_MANAGE'), ('ROOM_PLACEMENT_MANAGE'),
    ('CONNECTION_MANAGE'), ('STATISTICS_READ'), ('RESERVATION_MAINTENANCE')
) AS p(permission_code)
WHERE s.role_code = 'ADMIN'
   OR p.permission_code = 'READ'
   OR (s.role_code IN ('UNIVERSITY_STAFF','STUDENT','LECTURER')
       AND p.permission_code IN ('RESERVE','OWN_RESERVATION_MANAGE','OWN_ACTIVE_DEVICE_CONTROL'));

CREATE FUNCTION assign_viewer_to_new_user() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO user_role_state(user_id) VALUES (NEW.id);
    INSERT INTO role_assignment(user_id, role_code) VALUES (NEW.id, 'VIEWER');
    RETURN NEW;
END;
$$;

CREATE TRIGGER user_account_default_viewer
AFTER INSERT ON user_account
FOR EACH ROW EXECUTE FUNCTION assign_viewer_to_new_user();
