-- =============================================
-- i18n 管理菜单与权限播种：挂在「系统管理」目录(id=1)下，绑定 admin 角色
-- =============================================

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort, visible, status)
SELECT v.id, v.parent_id, v.menu_name, v.menu_type, v.path, v.component, v.permission, v.icon, v.sort, v.visible, v.status
FROM (VALUES
    (70, 1, '国际化管理', 'PAGE',   '/sys/i18n', 'sys/i18n/index', 'sys:i18n:list',   'Language', 12, 1, 1),
    (71, 70, '编辑翻译',  'BUTTON', NULL,        NULL,             'sys:i18n:edit',   NULL,       1, 1, 1),
    (72, 70, '导入翻译',  'BUTTON', NULL,        NULL,             'sys:i18n:import', NULL,       2, 1, 1),
    (73, 70, '导出翻译',  'BUTTON', NULL,        NULL,             'sys:i18n:export', NULL,       3, 1, 1)
) AS v(id, parent_id, menu_name, menu_type, path, component, permission, icon, sort, visible, status)
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = v.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.id IN (70, 71, 72, 73)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

-- 国际化管理菜单自身的显示名 i18n key
INSERT INTO i18n_message (message_key, locale, module, value)
SELECT 'sys.menu.70.name', 'zh-CN', 'sys', '国际化管理'
WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.70.name' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value)
SELECT 'sys.menu.70.name', 'en', 'sys', 'Internationalization'
WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.70.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value)
SELECT 'sys.menu.71.name', 'zh-CN', 'sys', '编辑翻译'
WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.71.name' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value)
SELECT 'sys.menu.71.name', 'en', 'sys', 'Edit Translation'
WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.71.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value)
SELECT 'sys.menu.72.name', 'zh-CN', 'sys', '导入翻译'
WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.72.name' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value)
SELECT 'sys.menu.72.name', 'en', 'sys', 'Import Translation'
WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.72.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value)
SELECT 'sys.menu.73.name', 'zh-CN', 'sys', '导出翻译'
WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.73.name' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value)
SELECT 'sys.menu.73.name', 'en', 'sys', 'Export Translation'
WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.73.name' AND m.locale = 'en');
