-- =============================================
-- 补齐 openapp 菜单(60~63)缺失的英文翻译
--
-- 背景：外部应用菜单的中文翻译在旧 menu_translation 表迁移时已带入
-- i18n_message，但英文(en)从未播种，导致 locale=en 时 MenuService 解析
-- sys.menu.6X.name 抛 NoSuchMessageException(已被代码兜底，此处补齐数据)。
-- 幂等：受 (message_key, locale) 唯一约束保护。
-- =============================================

INSERT INTO i18n_message (message_key, locale, module, value)
SELECT 'sys.menu.60.name', 'en', 'sys', 'External Applications'
WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.60.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value)
SELECT 'sys.menu.61.name', 'en', 'sys', 'Add Application'
WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.61.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value)
SELECT 'sys.menu.62.name', 'en', 'sys', 'Edit Application'
WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.62.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value)
SELECT 'sys.menu.63.name', 'en', 'sys', 'Delete Application'
WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.63.name' AND m.locale = 'en');
