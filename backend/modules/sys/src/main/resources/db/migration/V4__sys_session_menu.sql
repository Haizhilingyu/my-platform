-- =============================================
-- V4: 在线会话菜单种子（/sys/session）
-- =============================================

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort, visible, status)
SELECT 50, 1, '在线会话', 'PAGE', '/sys/session', 'sys/session/index', 'sys:user:session', 'Globe', 6, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 50);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, 50
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = 50);
