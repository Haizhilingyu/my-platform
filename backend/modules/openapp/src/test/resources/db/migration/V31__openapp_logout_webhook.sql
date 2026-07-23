-- =============================================
-- V31: openapp 登出 webhook（测试专用 schema，从 V1__init.sql 拆出）
-- 为 openapp_client 增加 logout_webhook_url 列：用户登出时向该 URL 推送 {event,userId,username,timestamp}
-- H2 + PostgreSQL 兼容：使用 ADD COLUMN IF NOT EXISTS（H2 2.x / PG 9.6+ 均支持）
-- =============================================

ALTER TABLE openapp_client ADD COLUMN IF NOT EXISTS logout_webhook_url VARCHAR(1024);
