INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, permission, sort, visible, status)
SELECT 8, 2, '用户解锁', 'BUTTON', 'sys:user:unlock', 6, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 8);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, 8
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 8);
