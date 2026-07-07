-- Sync IDENTITY sequences after seed inserts.
-- Flyway inserts with explicit IDs bypass sequence advancement, causing
-- duplicate key errors on subsequent JPA inserts (nextval returns a stale value).
-- This runs last (V99) so all module seed data is already present.

SELECT setval('sys_user_id_seq', COALESCE((SELECT MAX(id) FROM sys_user), 0) + 1, false);
SELECT setval('sys_role_id_seq', COALESCE((SELECT MAX(id) FROM sys_role), 0) + 1, false);
SELECT setval('sys_menu_id_seq', COALESCE((SELECT MAX(id) FROM sys_menu), 0) + 1, false);
SELECT setval('sys_unit_id_seq', COALESCE((SELECT MAX(id) FROM sys_unit), 0) + 1, false);
SELECT setval('sys_config_id_seq', COALESCE((SELECT MAX(id) FROM sys_config), 0) + 1, false);
