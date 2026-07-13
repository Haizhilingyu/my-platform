-- =============================================
-- 删除旧的菜单翻译表（已迁移至 i18n_message，sys.menu.<id>.name key）
-- V103__menu_translation.sql 保留为 Flyway 历史，不删除
-- =============================================

DROP TABLE IF EXISTS sys_menu_translation;
