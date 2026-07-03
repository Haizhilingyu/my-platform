-- =============================================
-- sys 模块初始数据：超级管理员 + 基础菜单
-- =============================================
-- 幂等写法说明：原 PG 专有的 `INSERT ... ON CONFLICT (...) DO NOTHING` 在 H2（即便处于
-- PostgreSQL 兼容模式）下不被支持。这里改用标准 SQL 的
-- `INSERT ... SELECT ... WHERE NOT EXISTS (...)`，在 PostgreSQL 15+ 与 H2 上语义完全
-- 等价（已存在则不插入、不报错），生产播种行为保持不变。

-- 默认单位：总部
INSERT INTO sys_unit (id, parent_id, unit_code, unit_name, sort, status)
SELECT 1, NULL, 'HQ', '总部', 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_unit WHERE unit_code = 'HQ');

-- 超级管理员用户 (密码: admin123, BCrypt加密)
INSERT INTO sys_user (id, username, password, real_name, unit_id, status)
SELECT 1, 'admin', '$2a$10$HH.rhJLgwoJdgh4Nu.NIeuRD.NMWvZP9fAOAC9cY13cmQq8XSAKRy', '超级管理员', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin');

-- 超级管理员角色
INSERT INTO sys_role (id, role_code, role_name, data_scope, status)
SELECT 1, 'admin', '超级管理员', 'ALL', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'admin');

-- 给 admin 绑定 admin 角色
INSERT INTO sys_user_role (user_id, role_id)
SELECT 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE user_id = 1 AND role_id = 1);

-- 基础菜单：系统管理目录（批量幂等：按主键 id 去重）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort, visible, status)
SELECT v.id, v.parent_id, v.menu_name, v.menu_type, v.path, v.component, v.permission, v.icon, v.sort, v.visible, v.status
FROM (VALUES
    (1,  NULL, '系统管理', 'DIRECTORY', '/sys',      NULL,                   NULL,              'Settings',        1, 1, 1),
    (2,  1,    '用户管理', 'PAGE',      '/sys/user', 'sys/user/index',       'sys:user:list',   'User',            1, 1, 1),
    (3,  2,    '用户新增', 'BUTTON',    NULL,        NULL,                   'sys:user:add',    NULL,              1, 1, 1),
    (4,  2,    '用户编辑', 'BUTTON',    NULL,        NULL,                   'sys:user:edit',   NULL,              2, 1, 1),
    (5,  2,    '用户删除', 'BUTTON',    NULL,        NULL,                   'sys:user:delete', NULL,              3, 1, 1),
    (6,  2,    '重置密码', 'BUTTON',    NULL,        NULL,                   'sys:user:reset',  NULL,              4, 1, 1),
    (7,  2,    '分配角色', 'BUTTON',    NULL,        NULL,                   'sys:user:role',   NULL,              5, 1, 1),
    (10, 1,    '角色管理', 'PAGE',      '/sys/role', 'sys/role/index',       'sys:role:list',   'UserFilled',      2, 1, 1),
    (11, 10,   '角色新增', 'BUTTON',    NULL,        NULL,                   'sys:role:add',    NULL,              1, 1, 1),
    (12, 10,   '角色编辑', 'BUTTON',    NULL,        NULL,                   'sys:role:edit',   NULL,              2, 1, 1),
    (13, 10,   '角色删除', 'BUTTON',    NULL,        NULL,                   'sys:role:delete', NULL,              3, 1, 1),
    (14, 10,   '权限分配', 'BUTTON',    NULL,        NULL,                   'sys:role:perm',   NULL,              4, 1, 1),
    (20, 1,    '菜单管理', 'PAGE',      '/sys/menu', 'sys/menu/index',       'sys:menu:list',   'Menu',            3, 1, 1),
    (21, 20,   '菜单新增', 'BUTTON',    NULL,        NULL,                   'sys:menu:add',    NULL,              1, 1, 1),
    (22, 20,   '菜单编辑', 'BUTTON',    NULL,        NULL,                   'sys:menu:edit',   NULL,              2, 1, 1),
    (23, 20,   '菜单删除', 'BUTTON',    NULL,        NULL,                   'sys:menu:delete', NULL,              3, 1, 1),
    (30, 1,    '单位管理', 'PAGE',      '/sys/unit', 'sys/unit/index',       'sys:unit:list',   'OfficeBuilding',  4, 1, 1),
    (31, 30,   '单位新增', 'BUTTON',    NULL,        NULL,                   'sys:unit:add',    NULL,              1, 1, 1),
    (32, 30,   '单位编辑', 'BUTTON',    NULL,        NULL,                   'sys:unit:edit',   NULL,              2, 1, 1),
    (33, 30,   '单位删除', 'BUTTON',    NULL,        NULL,                   'sys:unit:delete', NULL,              3, 1, 1),
    (40, 1,    '系统配置', 'PAGE',      '/sys/config','sys/config/index',    'sys:config:list', 'Tools',           5, 1, 1),
    (41, 40,   '配置新增', 'BUTTON',    NULL,        NULL,                   'sys:config:add',  NULL,              1, 1, 1),
    (42, 40,   '配置编辑', 'BUTTON',    NULL,        NULL,                   'sys:config:edit', NULL,              2, 1, 1)
) AS v(id, parent_id, menu_name, menu_type, path, component, permission, icon, sort, visible, status)
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = v.id);

-- 给 admin 角色绑定所有菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

-- 基础系统配置（批量幂等：按 config_key 去重）
INSERT INTO sys_config (config_key, config_value, config_type, description, category)
SELECT v.config_key, v.config_value, v.config_type, v.description, v.category
FROM (VALUES
    ('sys.password.min-length',    '8',                       'NUMBER', '密码最小长度',         'security'),
    ('sys.password.require-upper', 'true',                    'BOOLEAN','密码必须包含大写字母',  'security'),
    ('sys.user.default-avatar',    '/img/default-avatar.png', 'STRING', '默认头像路径',         'user'),
    ('sys.session.timeout',        '86400',                   'NUMBER', '会话超时（秒）',       'security')
) AS v(config_key, config_value, config_type, description, category)
WHERE NOT EXISTS (SELECT 1 FROM sys_config c WHERE c.config_key = v.config_key);
