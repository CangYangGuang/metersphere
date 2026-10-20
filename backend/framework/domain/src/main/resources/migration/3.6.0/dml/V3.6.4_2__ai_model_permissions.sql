-- Ensure upgraded installations expose AI model settings to the built-in system administrator.
INSERT INTO user_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), 'admin', 'SYSTEM_PARAMETER_SETTING_AI_MODEL:READ'
WHERE NOT EXISTS (
    SELECT 1
    FROM user_role_permission
    WHERE role_id = 'admin'
      AND permission_id = 'SYSTEM_PARAMETER_SETTING_AI_MODEL:READ'
);

INSERT INTO user_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), 'admin', 'SYSTEM_PARAMETER_SETTING_AI_MODEL:READ+UPDATE'
WHERE NOT EXISTS (
    SELECT 1
    FROM user_role_permission
    WHERE role_id = 'admin'
      AND permission_id = 'SYSTEM_PARAMETER_SETTING_AI_MODEL:READ+UPDATE'
);
