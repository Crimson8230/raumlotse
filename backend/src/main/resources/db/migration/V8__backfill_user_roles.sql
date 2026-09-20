-- Existing accounts only. Account creation and initial Admin provisioning are external.
INSERT INTO user_role_state(user_id)
SELECT id FROM user_account
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO role_assignment(user_id, role_code)
SELECT s.user_id, 'VIEWER' FROM user_role_state s
WHERE NOT EXISTS (SELECT 1 FROM role_assignment r WHERE r.user_id = s.user_id);

