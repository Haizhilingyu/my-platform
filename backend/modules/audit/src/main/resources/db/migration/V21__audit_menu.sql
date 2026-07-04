-- =============================================
-- audit 模块初始数据：审计日志菜单 + 绑定 admin 角色
-- =============================================
-- sys 模块 V2 播种时 audit 模块尚未加载，admin 不会自动获得 sys:audit:list 权限
-- （权限系统无 admin 隐式放行，见 PermissionAspect）。这里在 audit 模块独立补种。
-- 幂等写法与 sys V2 一致：INSERT ... SELECT ... WHERE NOT EXISTS，PG15+ 与 H2 等价。

-- 审计日志菜单（挂在「系统管理」目录 id=1 下）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort, visible, status)
SELECT 50, 1, '审计日志', 'PAGE', '/sys/audit', 'sys/audit/index', 'sys:audit:list', 'Document', 6, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 50);

-- 给 admin 角色（role_id=1）绑定审计日志菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, 50
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 50);
