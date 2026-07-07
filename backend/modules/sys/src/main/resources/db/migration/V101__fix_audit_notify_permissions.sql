-- Fix audit log menu: V21 used id=50 but V4 (session) already took it.
-- Notify publish permission was never seeded.
-- Both needed for admin role to access audit logs and assign publish permission.

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort, visible, status)
SELECT 51, 1, '审计日志', 'PAGE', '/sys/audit', 'sys/audit/index', 'sys:audit:list', 'Document', 8, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 51);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort, visible, status)
SELECT 55, 1, '通知发布', 'BUTTON', NULL, NULL, 'sys:notify:publish', NULL, 9, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 55);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.id IN (51, 55)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);
