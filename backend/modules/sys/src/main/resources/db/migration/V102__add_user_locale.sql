-- =============================================
-- B3: 为 sys_user 增加 locale 字段，存储用户语言偏好 (zh-CN / en)
-- 默认 zh-CN，与登录前 JwtAuthFilter 的回退值保持一致
-- =============================================

ALTER TABLE sys_user ADD COLUMN locale VARCHAR(10) NOT NULL DEFAULT 'zh-CN';

COMMENT ON COLUMN sys_user.locale IS '用户语言偏好 (zh-CN, en)';
