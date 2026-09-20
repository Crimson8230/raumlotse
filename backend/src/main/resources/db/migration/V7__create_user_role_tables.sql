CREATE TABLE user_role_state (
    user_id UUID PRIMARY KEY REFERENCES user_account(id),
    roles_version BIGINT NOT NULL DEFAULT 0 CHECK (roles_version >= 0)
);
CREATE TABLE role_assignment (
    user_id UUID NOT NULL REFERENCES user_role_state(user_id),
    role_code VARCHAR(32) NOT NULL CHECK (role_code IN ('ADMIN','UNIVERSITY_STAFF','STUDENT','LECTURER','VIEWER')),
    PRIMARY KEY (user_id, role_code)
);
CREATE INDEX ix_role_assignment_code ON role_assignment(role_code);
CREATE TABLE role_mutation_guard (id INTEGER PRIMARY KEY CHECK (id = 1));
INSERT INTO role_mutation_guard(id) VALUES (1);

