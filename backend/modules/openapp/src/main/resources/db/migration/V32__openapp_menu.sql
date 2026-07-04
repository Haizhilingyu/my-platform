-- =============================================
-- openapp 模块菜单播种：外部应用管理 + 按钮权限，绑定 admin 角色
-- =============================================
-- sys 模块 V2 播种时 openapp 模块尚未加载，admin 不会自动获得 sys:openapp:* 权限
-- （权限系统无 admin 隐式放行，见 PermissionAspect）。这里在 openapp 模块独立补种。
-- 幂等写法与 sys V2 / audit V21 一致：INSERT ... SELECT ... WHERE NOT EXISTS。

-- 外部应用管理菜单（挂在「系统管理」目录 id=1 下）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort, visible, status)
SELECT v.id, v.parent_id, v.menu_name, v.menu_type, v.path, v.component, v.permission, v.icon, v.sort, v.visible, v.status
FROM (VALUES
    (60, 1, '外部应用', 'PAGE',   '/sys/app', 'sys/app/index', 'sys:openapp:list',   'Apps',     7, 1, 1),
    (61, 60, '应用新增', 'BUTTON', NULL,      NULL,            'sys:openapp:add',    NULL,       1, 1, 1),
    (62, 60, '应用编辑', 'BUTTON', NULL,      NULL,            'sys:openapp:edit',   NULL,       2, 1, 1),
    (63, 60, '应用删除', 'BUTTON', NULL,      NULL,            'sys:openapp:delete', NULL,       3, 1, 1)
) AS v(id, parent_id, menu_name, menu_type, path, component, permission, icon, sort, visible, status)
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = v.id);

-- 给 admin 角色（role_id=1）绑定外部应用菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.id IN (60, 61, 62, 63)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);
