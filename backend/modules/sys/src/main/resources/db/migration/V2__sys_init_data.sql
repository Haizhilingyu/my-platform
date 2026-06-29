-- =============================================
-- sys 模块初始数据：超级管理员 + 基础菜单
-- =============================================

-- 默认单位：总部
INSERT INTO sys_unit (id, parent_id, unit_code, unit_name, sort, status)
VALUES (1, NULL, 'HQ', '总部', 0, 1)
ON CONFLICT (unit_code) DO NOTHING;

-- 超级管理员用户 (密码: admin123, BCrypt加密)
INSERT INTO sys_user (id, username, password, real_name, unit_id, status)
VALUES (1, 'admin', '$2a$10$HH.rhJLgwoJdgh4Nu.NIeuRD.NMWvZP9fAOAC9cY13cmQq8XSAKRy', '超级管理员', 1, 1)
ON CONFLICT (username) DO NOTHING;

-- 超级管理员角色
INSERT INTO sys_role (id, role_code, role_name, data_scope, status)
VALUES (1, 'admin', '超级管理员', 'ALL', 1)
ON CONFLICT (role_code) DO NOTHING;

-- 给 admin 绑定 admin 角色
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1)
ON CONFLICT DO NOTHING;

-- 基础菜单：系统管理目录
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort, visible, status) VALUES
(1,  NULL, '系统管理', 'DIRECTORY', '/sys',     NULL,                  NULL,             'Settings',   1, 1, 1),
(2,  1,    '用户管理', 'PAGE',      '/sys/user', 'sys/user/index',       'sys:user:list', 'User',       1, 1, 1),
(3,  2,    '用户新增', 'BUTTON',    NULL,         NULL,                  'sys:user:add',   NULL,         1, 1, 1),
(4,  2,    '用户编辑', 'BUTTON',    NULL,         NULL,                  'sys:user:edit',  NULL,         2, 1, 1),
(5,  2,    '用户删除', 'BUTTON',    NULL,         NULL,                  'sys:user:delete', NULL,         3, 1, 1),
(6,  2,    '重置密码', 'BUTTON',    NULL,         NULL,                  'sys:user:reset', NULL,         4, 1, 1),
(7,  2,    '分配角色', 'BUTTON',    NULL,         NULL,                  'sys:user:role',  NULL,         5, 1, 1),
(10, 1,    '角色管理', 'PAGE',      '/sys/role', 'sys/role/index',       'sys:role:list', 'UserFilled', 2, 1, 1),
(11, 10,   '角色新增', 'BUTTON',    NULL,         NULL,                  'sys:role:add',   NULL,         1, 1, 1),
(12, 10,   '角色编辑', 'BUTTON',    NULL,         NULL,                  'sys:role:edit',  NULL,         2, 1, 1),
(13, 10,   '角色删除', 'BUTTON',    NULL,         NULL,                  'sys:role:delete', NULL,         3, 1, 1),
(14, 10,   '权限分配', 'BUTTON',    NULL,         NULL,                  'sys:role:perm',  NULL,         4, 1, 1),
(20, 1,    '菜单管理', 'PAGE',      '/sys/menu', 'sys/menu/index',       'sys:menu:list', 'Menu',       3, 1, 1),
(21, 20,   '菜单新增', 'BUTTON',    NULL,         NULL,                  'sys:menu:add',   NULL,         1, 1, 1),
(22, 20,   '菜单编辑', 'BUTTON',    NULL,         NULL,                  'sys:menu:edit',  NULL,         2, 1, 1),
(23, 20,   '菜单删除', 'BUTTON',    NULL,         NULL,                  'sys:menu:delete', NULL,         3, 1, 1),
(30, 1,    '单位管理', 'PAGE',      '/sys/unit', 'sys/unit/index',       'sys:unit:list', 'OfficeBuilding', 4, 1, 1),
(31, 30,   '单位新增', 'BUTTON',    NULL,         NULL,                  'sys:unit:add',   NULL,         1, 1, 1),
(32, 30,   '单位编辑', 'BUTTON',    NULL,         NULL,                  'sys:unit:edit',  NULL,         2, 1, 1),
(33, 30,   '单位删除', 'BUTTON',    NULL,         NULL,                  'sys:unit:delete', NULL,         3, 1, 1),
(40, 1,    '系统配置', 'PAGE',      '/sys/config','sys/config/index',    'sys:config:list','Tools',     5, 1, 1),
(41, 40,   '配置新增', 'BUTTON',    NULL,         NULL,                  'sys:config:add', NULL,         1, 1, 1),
(42, 40,   '配置编辑', 'BUTTON',    NULL,         NULL,                  'sys:config:edit', NULL,         2, 1, 1)
ON CONFLICT DO NOTHING;

-- 给 admin 角色绑定所有菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu
ON CONFLICT DO NOTHING;

-- 基础系统配置
INSERT INTO sys_config (config_key, config_value, config_type, description, category) VALUES
('sys.password.min-length',   '8',       'NUMBER', '密码最小长度',       'security'),
('sys.password.require-upper', 'true',    'BOOLEAN', '密码必须包含大写字母', 'security'),
('sys.user.default-avatar',    '/img/default-avatar.png', 'STRING', '默认头像路径', 'user'),
('sys.session.timeout',        '86400',   'NUMBER', '会话超时（秒）',     'security')
ON CONFLICT (config_key) DO NOTHING;
