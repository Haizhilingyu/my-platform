-- ============================================================================
-- 平台初始数据库脚本（开发环境单一版本，无增量迁移管理）
--
-- 所有模块的表结构 DDL + 基础种子数据合并为一个 V1。
-- 菜单注册由各模块的 MenuContributor 在应用启动时代码注册（sys 模块 MenuBootstrap），
-- 不在此脚本中 INSERT 业务模块菜单。核心 sys 菜单保留为种子。
-- 后续正式发布时再引入增量版本管理。
-- ============================================================================

-- ===== sys 模块表结构 =====

-- =============================================
-- sys 模块初始化表结构
-- =============================================

-- 单位表
CREATE TABLE IF NOT EXISTS sys_unit (
    id          BIGSERIAL PRIMARY KEY,
    parent_id   BIGINT,
    unit_code   VARCHAR(64)  NOT NULL UNIQUE,
    unit_name   VARCHAR(128) NOT NULL,
    sort        INTEGER      NOT NULL DEFAULT 0,
    status      INTEGER      NOT NULL DEFAULT 1,
    remark      VARCHAR(500),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(64),
    updated_by  VARCHAR(64)
);
COMMENT ON TABLE sys_unit IS '单位/组织表';

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL UNIQUE,
    password    VARCHAR(128) NOT NULL,
    real_name   VARCHAR(64),
    email       VARCHAR(128),
    phone       VARCHAR(20),
    unit_id     BIGINT,
    avatar      VARCHAR(500),
    status      INTEGER      NOT NULL DEFAULT 1,
    remark      VARCHAR(500),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(64),
    updated_by  VARCHAR(64)
);
COMMENT ON TABLE sys_user IS '系统用户表';

-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGSERIAL PRIMARY KEY,
    role_code   VARCHAR(64)  NOT NULL UNIQUE,
    role_name   VARCHAR(64)  NOT NULL,
    data_scope  VARCHAR(20)  NOT NULL DEFAULT 'SELF',
    status      INTEGER      NOT NULL DEFAULT 1,
    remark      VARCHAR(500),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(64),
    updated_by  VARCHAR(64)
);
COMMENT ON TABLE sys_role IS '角色表';

-- 菜单/权限表
CREATE TABLE IF NOT EXISTS sys_menu (
    id          BIGSERIAL PRIMARY KEY,
    parent_id   BIGINT,
    menu_name   VARCHAR(64)  NOT NULL,
    menu_type   VARCHAR(20)  NOT NULL,
    path        VARCHAR(200),
    component   VARCHAR(200),
    permission  VARCHAR(100),
    icon        VARCHAR(64),
    sort        INTEGER      NOT NULL DEFAULT 0,
    visible     INTEGER      NOT NULL DEFAULT 1,
    status      INTEGER      NOT NULL DEFAULT 1,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(64),
    updated_by  VARCHAR(64)
);
COMMENT ON TABLE sys_menu IS '菜单/权限表';

-- 用户-角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id     BIGINT NOT NULL,
    role_id     BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);
COMMENT ON TABLE sys_user_role IS '用户-角色关联表';

-- 角色-菜单关联表
CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id     BIGINT NOT NULL,
    menu_id     BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
);
COMMENT ON TABLE sys_role_menu IS '角色-菜单关联表';

-- 系统配置表
CREATE TABLE IF NOT EXISTS sys_config (
    id           BIGSERIAL PRIMARY KEY,
    config_key   VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT,
    config_type  VARCHAR(20)  NOT NULL DEFAULT 'STRING',
    description  VARCHAR(200),
    category     VARCHAR(50)  NOT NULL DEFAULT 'default',
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(64),
    updated_by   VARCHAR(64)
);
COMMENT ON TABLE sys_config IS '系统配置表';

-- 索引
CREATE INDEX IF NOT EXISTS idx_sys_user_unit       ON sys_user(unit_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_status     ON sys_user(status);
CREATE INDEX IF NOT EXISTS idx_sys_menu_parent     ON sys_menu(parent_id);
CREATE INDEX IF NOT EXISTS idx_sys_menu_type       ON sys_menu(menu_type);
CREATE INDEX IF NOT EXISTS idx_sys_user_role_user  ON sys_user_role(user_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_role_role  ON sys_user_role(role_id);
CREATE INDEX IF NOT EXISTS idx_sys_role_menu_role  ON sys_role_menu(role_id);
CREATE INDEX IF NOT EXISTS idx_sys_role_menu_menu  ON sys_role_menu(menu_id);
CREATE INDEX IF NOT EXISTS idx_sys_config_category ON sys_config(category);

-- ===== sys 数据权限表 =====

-- =============================================
-- V3: 数据权限 —— 角色-自定义数据范围关联表
-- =============================================
-- 当角色的 data_scope = 'CUSTOM' 时，本表存储该角色可见的自定义单位集合。
-- 仅使用 BIGINT + 复合主键 + 标准 FK 语法，H2（PostgreSQL 兼容模式）与 PostgreSQL 均可执行。

CREATE TABLE IF NOT EXISTS sys_role_data_scope (
    role_id   BIGINT NOT NULL,
    unit_id   BIGINT NOT NULL,
    PRIMARY KEY (role_id, unit_id)
);
COMMENT ON TABLE sys_role_data_scope IS '角色-自定义数据范围关联表（data_scope=CUSTOM 时生效）';

ALTER TABLE sys_role_data_scope
    ADD CONSTRAINT fk_role_data_scope_role
    FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE CASCADE;

ALTER TABLE sys_role_data_scope
    ADD CONSTRAINT fk_role_data_scope_unit
    FOREIGN KEY (unit_id) REFERENCES sys_unit (id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_role_data_scope_role ON sys_role_data_scope(role_id);
CREATE INDEX IF NOT EXISTS idx_role_data_scope_unit ON sys_role_data_scope(unit_id);

-- ===== sys_user locale 列 =====

-- =============================================
-- B3: 为 sys_user 增加 locale 字段，存储用户语言偏好 (zh-CN / en)
-- 默认 zh-CN，与登录前 JwtAuthFilter 的回退值保持一致
-- =============================================

ALTER TABLE sys_user ADD COLUMN locale VARCHAR(10) NOT NULL DEFAULT 'zh-CN';

COMMENT ON COLUMN sys_user.locale IS '用户语言偏好 (zh-CN, en)';

-- ===== notify 模块表结构 =====

-- =============================================
-- notify 模块初始化表结构
--
-- 注意：版本号使用 V10 而非 V1，因为 sys 模块已占用 V1/V2/V3，
-- 同一 classpath:db/migration 下 V1 会冲突。
-- 模块单独部署时可改为 V1；与 sys 共部署时用 V10+。
-- 表结构 H2 (PostgreSQL 兼容模式) 与 PostgreSQL 14+ 均可执行。
-- =============================================

-- 消息主表
CREATE TABLE IF NOT EXISTS notify_message (
    id             BIGSERIAL    PRIMARY KEY,
    title          VARCHAR(200) NOT NULL,
    content        TEXT         NOT NULL,
    level          VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    sender_id      BIGINT,
    business_type  VARCHAR(64),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    expire_time    TIMESTAMP
);
COMMENT ON TABLE  notify_message IS '消息主表';
COMMENT ON COLUMN notify_message.level IS '紧急度：URGENT / IMPORTANT / NORMAL';

-- 接收范围表（每条消息 N 行：USER/ROLE/UNIT + 接收方 ID）
CREATE TABLE IF NOT EXISTS notify_recipient (
    id             BIGSERIAL    PRIMARY KEY,
    message_id     BIGINT       NOT NULL,
    recipient_type VARCHAR(20)  NOT NULL,
    recipient_id   BIGINT       NOT NULL,
    CONSTRAINT fk_notify_recipient_message
        FOREIGN KEY (message_id) REFERENCES notify_message(id) ON DELETE CASCADE
);
COMMENT ON TABLE  notify_recipient IS '消息接收范围表';
COMMENT ON COLUMN notify_recipient.recipient_type IS '接收方类型：USER / ROLE / UNIT';

-- 用户收件箱（每用户 × 每消息一行，携带 seq 用于断线重连补播）
CREATE TABLE IF NOT EXISTS notify_user_inbox (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    message_id   BIGINT       NOT NULL,
    seq          BIGINT       NOT NULL,
    delivered    BOOLEAN      NOT NULL DEFAULT FALSE,
    delivered_at TIMESTAMP,
    read_status  BOOLEAN      NOT NULL DEFAULT FALSE,
    read_time    TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notify_inbox_message
        FOREIGN KEY (message_id) REFERENCES notify_message(id) ON DELETE CASCADE,
    CONSTRAINT uk_notify_inbox_user_seq UNIQUE (user_id, seq)
);
COMMENT ON TABLE  notify_user_inbox IS '用户收件箱';
COMMENT ON COLUMN notify_user_inbox.seq IS '用户维度单调递增序号，用于断线重连补播';

-- 索引
CREATE INDEX IF NOT EXISTS idx_notify_message_level       ON notify_message(level);
CREATE INDEX IF NOT EXISTS idx_notify_message_created_at  ON notify_message(created_at);
CREATE INDEX IF NOT EXISTS idx_notify_recipient_message   ON notify_recipient(message_id);
CREATE INDEX IF NOT EXISTS idx_notify_recipient_type_id   ON notify_recipient(recipient_type, recipient_id);
CREATE INDEX IF NOT EXISTS idx_notify_inbox_user          ON notify_user_inbox(user_id);
CREATE INDEX IF NOT EXISTS idx_notify_inbox_user_delivered ON notify_user_inbox(user_id, delivered);
CREATE INDEX IF NOT EXISTS idx_notify_inbox_user_read     ON notify_user_inbox(user_id, read_status);

-- ===== audit 模块表结构 =====

-- =============================================
-- audit 模块初始化表结构
-- =============================================
-- 审计日志表：append-only，仅记录写入时间（无 updated_* / *_by 审计列）。

CREATE TABLE IF NOT EXISTS audit_log (
    id           BIGSERIAL    PRIMARY KEY,
    actor        VARCHAR(64),
    actor_type   VARCHAR(20),
    action       VARCHAR(64)  NOT NULL,
    target_type  VARCHAR(64),
    target_id    VARCHAR(64),
    ip           VARCHAR(64),
    user_agent   TEXT,
    params       TEXT,
    result       VARCHAR(20)  NOT NULL,
    error_msg    TEXT,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE audit_log IS '审计日志表';

CREATE INDEX IF NOT EXISTS idx_audit_actor_action_time ON audit_log(actor, action, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_created_at        ON audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_target            ON audit_log(target_type, target_id);

-- ===== openapp 模块表结构 =====

-- =============================================
-- openapp 模块初始化表结构
-- 外部应用（OAuth2 授权服务器）+ 持久化 JWK + 授权记录
-- H2 + PostgreSQL 兼容：数组用逗号分隔 TEXT 存储，不使用 PG 数组类型
-- =============================================

-- 外部应用（OAuth2 客户端）
CREATE TABLE IF NOT EXISTS openapp_client (
    id                          BIGSERIAL      PRIMARY KEY,
    client_id                   VARCHAR(128)   NOT NULL UNIQUE,
    client_secret               VARCHAR(256)   NOT NULL,
    client_name                 VARCHAR(128),
    redirect_uris               VARCHAR(1024),
    post_logout_redirect_uris   VARCHAR(1024),
    scopes                      VARCHAR(1024),
    grant_types                 VARCHAR(256),
    authentication_methods      VARCHAR(256),
    enabled                     BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP      NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE openapp_client IS '外部应用（OAuth2 客户端）表，client_secret 存 BCrypt 哈希';

-- OAuth2 授权记录（授权服务器授权服务使用）
CREATE TABLE IF NOT EXISTS oauth_authorization (
    id                          BIGSERIAL      PRIMARY KEY,
    registered_client_id        VARCHAR(128)   NOT NULL,
    principal_name              VARCHAR(256)   NOT NULL,
    access_token                VARCHAR(4096),
    access_token_expires_at     TIMESTAMP,
    refresh_token               VARCHAR(4096),
    attributes                  VARCHAR(4096),
    created_at                  TIMESTAMP      NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE oauth_authorization IS 'OAuth2 授权记录表（access_token/refresh_token 审计）';

-- 持久化 JWK 密钥存储（密钥轮转：active → grace → expired）
CREATE TABLE IF NOT EXISTS openapp_jwk (
    id                          BIGSERIAL      PRIMARY KEY,
    kid                         VARCHAR(128)   NOT NULL UNIQUE,
    key_type                    VARCHAR(32)    NOT NULL,
    key_data                    VARCHAR(8192)  NOT NULL,
    status                      VARCHAR(16)    NOT NULL DEFAULT 'active',
    created_at                  TIMESTAMP      NOT NULL DEFAULT NOW(),
    rotated_at                  TIMESTAMP
);
COMMENT ON TABLE openapp_jwk IS '持久化 JWK 密钥存储，key_data 为 AES 加密后的 RSAKey JSON';

-- 初始化一个 active 状态占位由应用启动时 JWK 轮转服务自动生成（若表为空）

-- openapp logout webhook 列

-- =============================================
-- V31: openapp 登出 webhook
-- 为 openapp_client 增加 logout_webhook_url 列：用户登出时向该 URL 推送 {event,userId,username,timestamp}
-- H2 + PostgreSQL 兼容：使用 ADD COLUMN IF NOT EXISTS（H2 2.x / PG 9.6+ 均支持）
-- =============================================

ALTER TABLE openapp_client ADD COLUMN IF NOT EXISTS logout_webhook_url VARCHAR(1024);
COMMENT ON COLUMN openapp_client.logout_webhook_url IS '登出 webhook URL，用户登出时 POST {event:logout,userId,username,timestamp}';

-- ===== i18n 模块表结构 + 种子数据 =====

-- =============================================
-- i18n 模块初始化：i18n_message 表结构 + 种子数据
-- =============================================
-- 种子来源：backend messages*.properties + frontend i18n TS + V103 菜单 en 翻译
-- 种子由 I18nSeedGenerator 生成（src/main/scripts/I18nSeedGenerator.java），可重新生成

CREATE TABLE IF NOT EXISTS i18n_message (
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    message_key  VARCHAR(200) NOT NULL,
    locale       VARCHAR(10)  NOT NULL,
    module       VARCHAR(50)  NOT NULL,
    value        TEXT         NOT NULL,
    description  VARCHAR(500),
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   VARCHAR(64),
    updated_by   VARCHAR(64),
    CONSTRAINT uk_i18n_key_locale UNIQUE (message_key, locale),
    CONSTRAINT ck_i18n_locale CHECK (locale IN ('zh-CN','en'))
);
CREATE INDEX IF NOT EXISTS idx_i18n_locale ON i18n_message(locale);
CREATE INDEX IF NOT EXISTS idx_i18n_module_locale ON i18n_message(module, locale);

-- ===== Generated i18n seed data (1053 rows) =====
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.access.denied', 'zh-CN', 'platform-common', '无权限访问' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.access.denied' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.validation.failed', 'zh-CN', 'platform-common', '参数校验失败' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.validation.failed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.body.malformed', 'zh-CN', 'platform-common', '请求体格式错误' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.body.malformed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.param.missing', 'zh-CN', 'platform-common', '缺少必需的请求参数: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.param.missing' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.system', 'zh-CN', 'platform-common', '系统内部错误' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.system' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.account.locked', 'zh-CN', 'platform-common', '账号已锁定，请联系管理员或稍后重试' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.account.locked' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.resource.not.found', 'zh-CN', 'platform-common', '{0} 不存在: {1}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.resource.not.found' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.auth.captcha.required', 'zh-CN', 'platform-common', '请输入验证码' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.auth.captcha.required' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.auth.captcha.invalid', 'zh-CN', 'platform-common', '验证码错误或已过期' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.auth.captcha.invalid' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.auth.method.unsupported', 'zh-CN', 'platform-common', '不支持的登录方式: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.auth.method.unsupported' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.auth.not.login', 'zh-CN', 'platform-common', '未登录' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.auth.not.login' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.auth.bad.credentials', 'zh-CN', 'platform-common', '用户名或密码错误' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.auth.bad.credentials' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.user.disabled', 'zh-CN', 'platform-common', '用户已被禁用' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.user.disabled' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'user.username.exists', 'zh-CN', 'platform-common', '用户名已存在: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'user.username.exists' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'user.delete.forbidden', 'zh-CN', 'platform-common', '无权删除数据权限范围外的用户' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'user.delete.forbidden' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'user.not.found.by.username', 'zh-CN', 'platform-common', '用户不存在: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'user.not.found.by.username' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'role.code.exists', 'zh-CN', 'platform-common', '角色编码已存在: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'role.code.exists' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'menu.parent.self', 'zh-CN', 'platform-common', '上级菜单不能是自己' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'menu.parent.self' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'menu.has.children', 'zh-CN', 'platform-common', '存在子菜单，无法删除' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'menu.has.children' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'config.key.exists', 'zh-CN', 'platform-common', '配置键已存在: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'config.key.exists' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'unit.code.exists', 'zh-CN', 'platform-common', '单位编码已存在: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'unit.code.exists' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'unit.parent.self', 'zh-CN', 'platform-common', '上级单位不能是自己' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'unit.parent.self' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'unit.has.children', 'zh-CN', 'platform-common', '存在子单位，无法删除' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'unit.has.children' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'session.not.found', 'zh-CN', 'platform-common', '会话不存在或已过期' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'session.not.found' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'session.revoke.forbidden', 'zh-CN', 'platform-common', '无权撤销他人会话' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'session.revoke.forbidden' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.permission.denied', 'zh-CN', 'platform-common', '无权限: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.permission.denied' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.scope.no.token', 'zh-CN', 'platform-common', '未授权：缺少有效的 OAuth2 access_token' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.scope.no.token' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.scope.not.oauth2', 'zh-CN', 'platform-common', '非 OAuth2 令牌：期望 OAuth2 access_token' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.scope.not.oauth2' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.scope.missing', 'zh-CN', 'platform-common', '缺少 OAuth2 scope: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.scope.missing' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'ldap.empty.credentials', 'zh-CN', 'platform-common', '用户名和密码不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'ldap.empty.credentials' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'ldap.auth.failed', 'zh-CN', 'platform-common', 'LDAP 认证失败：用户名或密码错误' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'ldap.auth.failed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'ldap.service.unavailable', 'zh-CN', 'platform-common', 'LDAP 服务不可用: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'ldap.service.unavailable' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'ldap.attribute.error', 'zh-CN', 'platform-common', 'LDAP 读取用户属性失败' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'ldap.attribute.error' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'ldap.user.not.created', 'zh-CN', 'platform-common', '用户不存在且未开启自动创建: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'ldap.user.not.created' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.method.password', 'zh-CN', 'platform-common', '账号密码登录' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.method.password' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.method.ldap', 'zh-CN', 'platform-common', 'LDAP 登录' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.method.ldap' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.username.notBlank', 'zh-CN', 'sys', '用户名不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.username.notBlank' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.username.size', 'zh-CN', 'sys', '用户名长度需在3-32之间' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.username.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.username.pattern', 'zh-CN', 'sys', '用户名只能包含字母、数字、下划线' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.username.pattern' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.password.notBlank', 'zh-CN', 'sys', '密码不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.password.notBlank' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.password.size', 'zh-CN', 'sys', '密码长度需在6-32之间' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.password.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.realName.size', 'zh-CN', 'sys', '姓名长度不能超过50' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.realName.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.email.pattern', 'zh-CN', 'sys', '邮箱格式不正确' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.email.pattern' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.phone.pattern', 'zh-CN', 'sys', '手机号格式不正确' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.phone.pattern' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.phone.size', 'zh-CN', 'sys', '手机号长度不能超过20' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.phone.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.email.size', 'zh-CN', 'sys', '邮箱长度不能超过100' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.email.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.avatar.size', 'zh-CN', 'sys', '头像URL长度不能超过500' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.avatar.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.remark.size', 'zh-CN', 'sys', '备注长度不能超过200' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.remark.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.status.invalid', 'zh-CN', 'sys', '状态值非法' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.status.invalid' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.role.roleName.notBlank', 'zh-CN', 'sys', '角色名称不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.role.roleName.notBlank' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.role.roleName.size', 'zh-CN', 'sys', '角色名称长度不能超过100' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.role.roleName.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.role.roleCode.notBlank', 'zh-CN', 'sys', '角色编码不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.role.roleCode.notBlank' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.role.roleCode.pattern', 'zh-CN', 'sys', '角色编码只能包含字母、数字、下划线' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.role.roleCode.pattern' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.role.roleCode.size', 'zh-CN', 'sys', '角色编码长度需在3-50之间' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.role.roleCode.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.role.dataScope.notNull', 'zh-CN', 'sys', '数据范围不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.role.dataScope.notNull' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.role.dataScope.size', 'zh-CN', 'sys', '数据范围标识长度不能超过20' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.role.dataScope.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.role.status.invalid', 'zh-CN', 'sys', '状态值非法' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.role.status.invalid' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.role.remark.size', 'zh-CN', 'sys', '备注长度不能超过200' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.role.remark.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.menuName.notBlank', 'zh-CN', 'sys', '菜单名称不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.menuName.notBlank' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.menuName.size', 'zh-CN', 'sys', '菜单名称长度不能超过50' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.menuName.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.menuType.notNull', 'zh-CN', 'sys', '菜单类型不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.menuType.notNull' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.menuType.size', 'zh-CN', 'sys', '菜单类型长度不能超过20' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.menuType.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.path.size', 'zh-CN', 'sys', '路由路径长度不能超过200' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.path.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.component.size', 'zh-CN', 'sys', '组件路径长度不能超过200' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.component.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.permission.size', 'zh-CN', 'sys', '权限标识长度不能超过100' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.permission.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.icon.size', 'zh-CN', 'sys', '图标长度不能超过100' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.icon.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.sort.min', 'zh-CN', 'sys', '排序值不能为负数' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.sort.min' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.visible.notNull', 'zh-CN', 'sys', '是否可见不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.visible.notNull' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.visible.range', 'zh-CN', 'sys', '可见性值非法' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.visible.range' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.status.notNull', 'zh-CN', 'sys', '状态不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.status.notNull' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.status.invalid', 'zh-CN', 'sys', '状态值非法' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.status.invalid' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.config.configKey.notBlank', 'zh-CN', 'sys', '配置键不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.config.configKey.notBlank' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.config.configKey.pattern', 'zh-CN', 'sys', '配置键只能包含字母、数字、点、下划线、连字符' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.config.configKey.pattern' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.config.configKey.size', 'zh-CN', 'sys', '配置键长度不能超过100' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.config.configKey.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.config.configValue.notNull', 'zh-CN', 'sys', '配置值不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.config.configValue.notNull' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.config.configValue.size', 'zh-CN', 'sys', '配置值长度不能超过2000' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.config.configValue.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.config.configName.notBlank', 'zh-CN', 'sys', '配置名称不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.config.configName.notBlank' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.config.configName.size', 'zh-CN', 'sys', '配置名称长度需在2-100之间' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.config.configName.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.config.configType.notNull', 'zh-CN', 'sys', '配置类型不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.config.configType.notNull' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.config.configType.size', 'zh-CN', 'sys', '配置类型长度不能超过50' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.config.configType.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.config.description.size', 'zh-CN', 'sys', '描述长度不能超过500' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.config.description.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.config.category.size', 'zh-CN', 'sys', '分类长度不能超过50' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.config.category.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.unit.unitCode.notBlank', 'zh-CN', 'sys', '单位编码不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.unit.unitCode.notBlank' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.unit.unitCode.size', 'zh-CN', 'sys', '单位编码长度需在3-50之间' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.unit.unitCode.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.unit.unitCode.pattern', 'zh-CN', 'sys', '单位编码只能包含字母、数字、下划线' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.unit.unitCode.pattern' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.unit.unitName.notBlank', 'zh-CN', 'sys', '单位名称不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.unit.unitName.notBlank' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.unit.unitName.size', 'zh-CN', 'sys', '单位名称长度不能超过100' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.unit.unitName.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.unit.sort.min', 'zh-CN', 'sys', '排序值不能为负数' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.unit.sort.min' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.unit.status.invalid', 'zh-CN', 'sys', '状态值非法' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.unit.status.invalid' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.unit.remark.size', 'zh-CN', 'sys', '备注长度不能超过200' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.unit.remark.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.login.method.size', 'zh-CN', 'sys', '登录方式长度不能超过32' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.login.method.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.login.username.notBlank', 'zh-CN', 'sys', '用户名不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.login.username.notBlank' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.login.username.size', 'zh-CN', 'sys', '用户名长度不能超过32' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.login.username.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.login.password.notBlank', 'zh-CN', 'sys', '密码不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.login.password.notBlank' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.login.password.size', 'zh-CN', 'sys', '密码长度不能超过32' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.login.password.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.login.captchaId.size', 'zh-CN', 'sys', '验证码标识长度不能超过64' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.login.captchaId.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.login.captchaCode.size', 'zh-CN', 'sys', '验证码长度不能超过6' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.login.captchaCode.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.app.clientName.notBlank', 'zh-CN', 'openapp', '应用名称不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.app.clientName.notBlank' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.app.clientName.size', 'zh-CN', 'openapp', '应用名称长度不能超过100' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.app.clientName.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.app.clientId.pattern', 'zh-CN', 'openapp', '客户端ID只能包含字母、数字、下划线、连字符' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.app.clientId.pattern' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.app.redirectUris.notEmpty', 'zh-CN', 'openapp', 'redirectUris 不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.app.redirectUris.notEmpty' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.app.scopes.notEmpty', 'zh-CN', 'openapp', 'scopes 不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.app.scopes.notEmpty' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.app.grantTypes.notEmpty', 'zh-CN', 'openapp', 'grantTypes 不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.app.grantTypes.notEmpty' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.app.enabled.notNull', 'zh-CN', 'openapp', 'enabled 不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.app.enabled.notNull' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.controller.ids.notEmpty', 'zh-CN', 'platform-common', '删除ID列表不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.controller.ids.notEmpty' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.controller.roleIds.notEmpty', 'zh-CN', 'platform-common', '角色ID列表不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.controller.roleIds.notEmpty' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.controller.newPassword.notBlank', 'zh-CN', 'platform-common', '新密码不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.controller.newPassword.notBlank' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.controller.newPassword.size', 'zh-CN', 'platform-common', '密码长度需在6-32之间' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.controller.newPassword.size' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.controller.menuIds.notEmpty', 'zh-CN', 'platform-common', '菜单ID列表不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.controller.menuIds.notEmpty' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.controller.configList.notEmpty', 'zh-CN', 'platform-common', '配置列表不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.controller.configList.notEmpty' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.controller.locale.pattern', 'zh-CN', 'platform-common', '语言格式不正确，仅支持 zh-CN 或 en' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.controller.locale.pattern' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'profile.locale.updated', 'zh-CN', 'platform-common', '语言偏好已更新' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'profile.locale.updated' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'resource.user', 'zh-CN', 'platform-common', '用户' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'resource.user' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'resource.role', 'zh-CN', 'platform-common', '角色' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'resource.role' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'resource.menu', 'zh-CN', 'platform-common', '菜单' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'resource.menu' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'resource.config', 'zh-CN', 'platform-common', '配置' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'resource.config' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'resource.unit', 'zh-CN', 'platform-common', '单位' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'resource.unit' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'resource.app', 'zh-CN', 'platform-common', '外部应用' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'resource.app' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'resource.inbox_message', 'zh-CN', 'platform-common', '收件箱消息' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'resource.inbox_message' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.access.denied', 'en', 'platform-common', 'Access denied' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.access.denied' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.validation.failed', 'en', 'platform-common', 'Validation failed' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.validation.failed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.body.malformed', 'en', 'platform-common', 'Malformed request body' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.body.malformed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.param.missing', 'en', 'platform-common', 'Missing required request parameter: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.param.missing' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.system', 'en', 'platform-common', 'Internal server error' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.system' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.account.locked', 'en', 'platform-common', 'Account is locked, please contact the administrator or try again later' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.account.locked' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.resource.not.found', 'en', 'platform-common', '{0} does not exist: {1}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.resource.not.found' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.auth.captcha.required', 'en', 'platform-common', 'Captcha is required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.auth.captcha.required' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.auth.captcha.invalid', 'en', 'platform-common', 'Invalid or expired captcha' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.auth.captcha.invalid' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.auth.method.unsupported', 'en', 'platform-common', 'Unsupported login method: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.auth.method.unsupported' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.auth.not.login', 'en', 'platform-common', 'Not logged in' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.auth.not.login' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.auth.bad.credentials', 'en', 'platform-common', 'Invalid username or password' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.auth.bad.credentials' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.user.disabled', 'en', 'platform-common', 'User has been disabled' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.user.disabled' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'user.username.exists', 'en', 'platform-common', 'Username already exists: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'user.username.exists' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'user.delete.forbidden', 'en', 'platform-common', 'Forbidden: cannot delete users outside your data permission scope' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'user.delete.forbidden' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'user.not.found.by.username', 'en', 'platform-common', 'User not found: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'user.not.found.by.username' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'role.code.exists', 'en', 'platform-common', 'Role code already exists: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'role.code.exists' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'menu.parent.self', 'en', 'platform-common', 'Parent menu cannot be itself' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'menu.parent.self' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'menu.has.children', 'en', 'platform-common', 'Has child menus, cannot delete' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'menu.has.children' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'config.key.exists', 'en', 'platform-common', 'Config key already exists: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'config.key.exists' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'unit.code.exists', 'en', 'platform-common', 'Unit code already exists: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'unit.code.exists' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'unit.parent.self', 'en', 'platform-common', 'Parent unit cannot be itself' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'unit.parent.self' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'unit.has.children', 'en', 'platform-common', 'Has child units, cannot delete' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'unit.has.children' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'session.not.found', 'en', 'platform-common', 'Session not found or expired' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'session.not.found' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'session.revoke.forbidden', 'en', 'platform-common', 'Forbidden: cannot revoke other users'' sessions' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'session.revoke.forbidden' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.permission.denied', 'en', 'platform-common', 'Permission denied: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.permission.denied' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.scope.no.token', 'en', 'platform-common', 'Unauthorized: missing valid OAuth2 access_token' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.scope.no.token' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.scope.not.oauth2', 'en', 'platform-common', 'Not an OAuth2 token: expected OAuth2 access_token' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.scope.not.oauth2' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.scope.missing', 'en', 'platform-common', 'Missing OAuth2 scope: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.scope.missing' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'ldap.empty.credentials', 'en', 'platform-common', 'Username and password must not be empty' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'ldap.empty.credentials' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'ldap.auth.failed', 'en', 'platform-common', 'LDAP authentication failed: invalid username or password' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'ldap.auth.failed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'ldap.service.unavailable', 'en', 'platform-common', 'LDAP service unavailable: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'ldap.service.unavailable' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'ldap.attribute.error', 'en', 'platform-common', 'Failed to read LDAP user attributes' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'ldap.attribute.error' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'ldap.user.not.created', 'en', 'platform-common', 'User does not exist and auto-creation is disabled: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'ldap.user.not.created' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.method.password', 'en', 'platform-common', 'Password' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.method.password' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.method.ldap', 'en', 'platform-common', 'LDAP' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.method.ldap' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.username.notBlank', 'en', 'sys', 'Username is required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.username.notBlank' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.username.size', 'en', 'sys', 'Username must be between 3 and 32 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.username.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.username.pattern', 'en', 'sys', 'Username can only contain letters, digits, and underscores' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.username.pattern' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.password.notBlank', 'en', 'sys', 'Password is required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.password.notBlank' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.password.size', 'en', 'sys', 'Password must be between 6 and 32 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.password.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.realName.size', 'en', 'sys', 'Real name must not exceed 50 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.realName.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.email.pattern', 'en', 'sys', 'Invalid email format' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.email.pattern' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.phone.pattern', 'en', 'sys', 'Invalid phone number format' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.phone.pattern' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.phone.size', 'en', 'sys', 'Phone number must not exceed 20 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.phone.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.email.size', 'en', 'sys', 'Email must not exceed 100 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.email.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.avatar.size', 'en', 'sys', 'Avatar URL must not exceed 500 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.avatar.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.remark.size', 'en', 'sys', 'Remark must not exceed 200 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.remark.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.user.status.invalid', 'en', 'sys', 'Invalid status value' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.user.status.invalid' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.role.roleName.notBlank', 'en', 'sys', 'Role name is required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.role.roleName.notBlank' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.role.roleName.size', 'en', 'sys', 'Role name must not exceed 100 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.role.roleName.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.role.roleCode.notBlank', 'en', 'sys', 'Role code is required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.role.roleCode.notBlank' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.role.roleCode.pattern', 'en', 'sys', 'Role code can only contain letters, digits, and underscores' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.role.roleCode.pattern' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.role.roleCode.size', 'en', 'sys', 'Role code must be between 3 and 50 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.role.roleCode.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.role.dataScope.notNull', 'en', 'sys', 'Data scope is required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.role.dataScope.notNull' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.role.dataScope.size', 'en', 'sys', 'Data scope identifier must not exceed 20 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.role.dataScope.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.role.status.invalid', 'en', 'sys', 'Invalid status value' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.role.status.invalid' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.role.remark.size', 'en', 'sys', 'Remark must not exceed 200 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.role.remark.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.menuName.notBlank', 'en', 'sys', 'Menu name is required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.menuName.notBlank' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.menuName.size', 'en', 'sys', 'Menu name must not exceed 50 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.menuName.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.menuType.notNull', 'en', 'sys', 'Menu type is required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.menuType.notNull' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.menuType.size', 'en', 'sys', 'Menu type must not exceed 20 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.menuType.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.path.size', 'en', 'sys', 'Route path must not exceed 200 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.path.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.component.size', 'en', 'sys', 'Component path must not exceed 200 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.component.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.permission.size', 'en', 'sys', 'Permission identifier must not exceed 100 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.permission.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.icon.size', 'en', 'sys', 'Icon must not exceed 100 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.icon.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.sort.min', 'en', 'sys', 'Sort value must not be negative' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.sort.min' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.visible.notNull', 'en', 'sys', 'Visibility is required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.visible.notNull' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.visible.range', 'en', 'sys', 'Invalid visibility value' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.visible.range' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.status.notNull', 'en', 'sys', 'Status is required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.status.notNull' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.menu.status.invalid', 'en', 'sys', 'Invalid status value' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.menu.status.invalid' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.config.configKey.notBlank', 'en', 'sys', 'Config key is required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.config.configKey.notBlank' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.config.configKey.pattern', 'en', 'sys', 'Config key can only contain letters, digits, dots, underscores, and hyphens' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.config.configKey.pattern' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.config.configKey.size', 'en', 'sys', 'Config key must not exceed 100 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.config.configKey.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.config.configValue.notNull', 'en', 'sys', 'Config value is required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.config.configValue.notNull' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.config.configValue.size', 'en', 'sys', 'Config value must not exceed 2000 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.config.configValue.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.config.configName.notBlank', 'en', 'sys', 'Config name is required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.config.configName.notBlank' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.config.configName.size', 'en', 'sys', 'Config name must be between 2 and 100 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.config.configName.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.config.configType.notNull', 'en', 'sys', 'Config type is required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.config.configType.notNull' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.config.configType.size', 'en', 'sys', 'Config type must not exceed 50 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.config.configType.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.config.description.size', 'en', 'sys', 'Description must not exceed 500 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.config.description.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.config.category.size', 'en', 'sys', 'Category must not exceed 50 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.config.category.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.unit.unitCode.notBlank', 'en', 'sys', 'Unit code is required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.unit.unitCode.notBlank' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.unit.unitCode.size', 'en', 'sys', 'Unit code must be between 3 and 50 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.unit.unitCode.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.unit.unitCode.pattern', 'en', 'sys', 'Unit code can only contain letters, digits, and underscores' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.unit.unitCode.pattern' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.unit.unitName.notBlank', 'en', 'sys', 'Unit name is required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.unit.unitName.notBlank' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.unit.unitName.size', 'en', 'sys', 'Unit name must not exceed 100 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.unit.unitName.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.unit.sort.min', 'en', 'sys', 'Sort value must not be negative' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.unit.sort.min' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.unit.status.invalid', 'en', 'sys', 'Invalid status value' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.unit.status.invalid' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.unit.remark.size', 'en', 'sys', 'Remark must not exceed 200 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.unit.remark.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.login.method.size', 'en', 'sys', 'Login method must not exceed 32 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.login.method.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.login.username.notBlank', 'en', 'sys', 'Username is required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.login.username.notBlank' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.login.username.size', 'en', 'sys', 'Username must not exceed 32 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.login.username.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.login.password.notBlank', 'en', 'sys', 'Password is required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.login.password.notBlank' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.login.password.size', 'en', 'sys', 'Password must not exceed 32 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.login.password.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.login.captchaId.size', 'en', 'sys', 'Captcha ID must not exceed 64 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.login.captchaId.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.login.captchaCode.size', 'en', 'sys', 'Captcha must not exceed 6 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.login.captchaCode.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.app.clientName.notBlank', 'en', 'openapp', 'Application name is required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.app.clientName.notBlank' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.app.clientName.size', 'en', 'openapp', 'Application name must not exceed 100 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.app.clientName.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.app.clientId.pattern', 'en', 'openapp', 'Client ID can only contain letters, digits, underscores, and hyphens' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.app.clientId.pattern' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.app.redirectUris.notEmpty', 'en', 'openapp', 'redirectUris must not be empty' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.app.redirectUris.notEmpty' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.app.scopes.notEmpty', 'en', 'openapp', 'scopes must not be empty' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.app.scopes.notEmpty' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.app.grantTypes.notEmpty', 'en', 'openapp', 'grantTypes must not be empty' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.app.grantTypes.notEmpty' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.app.enabled.notNull', 'en', 'openapp', 'enabled must not be null' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.app.enabled.notNull' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.controller.ids.notEmpty', 'en', 'platform-common', 'Delete ID list must not be empty' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.controller.ids.notEmpty' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.controller.roleIds.notEmpty', 'en', 'platform-common', 'Role ID list must not be empty' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.controller.roleIds.notEmpty' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.controller.newPassword.notBlank', 'en', 'platform-common', 'New password is required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.controller.newPassword.notBlank' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.controller.newPassword.size', 'en', 'platform-common', 'Password must be between 6 and 32 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.controller.newPassword.size' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.controller.menuIds.notEmpty', 'en', 'platform-common', 'Menu ID list must not be empty' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.controller.menuIds.notEmpty' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.controller.configList.notEmpty', 'en', 'platform-common', 'Config list must not be empty' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.controller.configList.notEmpty' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'validation.controller.locale.pattern', 'en', 'platform-common', 'Invalid locale format, only zh-CN or en is supported' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'validation.controller.locale.pattern' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'profile.locale.updated', 'en', 'platform-common', 'Language preference updated' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'profile.locale.updated' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'resource.user', 'en', 'platform-common', 'User' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'resource.user' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'resource.role', 'en', 'platform-common', 'Role' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'resource.role' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'resource.menu', 'en', 'platform-common', 'Menu' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'resource.menu' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'resource.config', 'en', 'platform-common', 'Configuration' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'resource.config' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'resource.unit', 'en', 'platform-common', 'Unit' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'resource.unit' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'resource.app', 'en', 'platform-common', 'Application' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'resource.app' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'resource.inbox_message', 'en', 'platform-common', 'Inbox message' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'resource.inbox_message' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.save', 'zh-CN', 'frontend', '保存' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.save' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.cancel', 'zh-CN', 'frontend', '取消' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.cancel' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.delete', 'zh-CN', 'frontend', '删除' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.delete' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.edit', 'zh-CN', 'frontend', '编辑' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.edit' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.add', 'zh-CN', 'frontend', '新增' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.add' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.search', 'zh-CN', 'frontend', '查询' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.search' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.reset', 'zh-CN', 'frontend', '重置' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.reset' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.confirm', 'zh-CN', 'frontend', '确认' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.confirm' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.close', 'zh-CN', 'frontend', '关闭' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.close' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.operation', 'zh-CN', 'frontend', '操作' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.operation' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.status', 'zh-CN', 'frontend', '状态' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.status' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.remark', 'zh-CN', 'frontend', '备注' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.remark' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.loading', 'zh-CN', 'frontend', '加载中...' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.loading' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.noData', 'zh-CN', 'frontend', '暂无数据' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.noData' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.modifySuccess', 'zh-CN', 'frontend', '修改成功' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.modifySuccess' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.createSuccess', 'zh-CN', 'frontend', '新增成功' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.createSuccess' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.deleteSuccess', 'zh-CN', 'frontend', '删除成功' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.deleteSuccess' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.deleteFailed', 'zh-CN', 'frontend', '删除失败' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.deleteFailed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.operationFailed', 'zh-CN', 'frontend', '操作失败' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.operationFailed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.queryFailed', 'zh-CN', 'frontend', '查询失败' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.queryFailed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.saveSuccess', 'zh-CN', 'frontend', '保存成功' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.saveSuccess' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.confirmDelete', 'zh-CN', 'frontend', '确定删除此记录？' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.confirmDelete' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.selectedRequired', 'zh-CN', 'frontend', '请先选择记录' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.selectedRequired' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.copied', 'zh-CN', 'frontend', '已复制到剪贴板' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.copied' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.copyFailed', 'zh-CN', 'frontend', '复制失败，请手动选择复制' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.copyFailed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.localeChanged', 'zh-CN', 'frontend', '语言已切换' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.localeChanged' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.language', 'zh-CN', 'frontend', '语言' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.language' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.required', 'zh-CN', 'frontend', '不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.required' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.lengthRule', 'zh-CN', 'frontend', '长度需在{min}-{max}之间' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.lengthRule' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.maxLengthRule', 'zh-CN', 'frontend', '长度不能超过{max}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.maxLengthRule' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.invalidFormat', 'zh-CN', 'frontend', '格式不正确' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.invalidFormat' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.save', 'en', 'frontend', 'Save' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.save' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.cancel', 'en', 'frontend', 'Cancel' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.cancel' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.delete', 'en', 'frontend', 'Delete' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.delete' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.edit', 'en', 'frontend', 'Edit' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.edit' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.add', 'en', 'frontend', 'Add' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.add' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.search', 'en', 'frontend', 'Search' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.search' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.reset', 'en', 'frontend', 'Reset' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.reset' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.confirm', 'en', 'frontend', 'Confirm' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.confirm' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.close', 'en', 'frontend', 'Close' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.close' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.operation', 'en', 'frontend', 'Action' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.operation' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.status', 'en', 'frontend', 'Status' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.status' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.remark', 'en', 'frontend', 'Remark' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.remark' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.loading', 'en', 'frontend', 'Loading...' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.loading' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.noData', 'en', 'frontend', 'No data' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.noData' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.modifySuccess', 'en', 'frontend', 'Updated successfully' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.modifySuccess' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.createSuccess', 'en', 'frontend', 'Created successfully' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.createSuccess' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.deleteSuccess', 'en', 'frontend', 'Deleted successfully' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.deleteSuccess' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.deleteFailed', 'en', 'frontend', 'Delete failed' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.deleteFailed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.operationFailed', 'en', 'frontend', 'Operation failed' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.operationFailed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.queryFailed', 'en', 'frontend', 'Query failed' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.queryFailed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.saveSuccess', 'en', 'frontend', 'Saved successfully' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.saveSuccess' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.confirmDelete', 'en', 'frontend', 'Are you sure you want to delete this record?' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.confirmDelete' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.selectedRequired', 'en', 'frontend', 'Please select a record first' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.selectedRequired' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.copied', 'en', 'frontend', 'Copied to clipboard' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.copied' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.copyFailed', 'en', 'frontend', 'Copy failed, please select and copy manually' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.copyFailed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.localeChanged', 'en', 'frontend', 'Language switched' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.localeChanged' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.language', 'en', 'frontend', 'Language' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.language' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.required', 'en', 'frontend', 'Required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.required' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.lengthRule', 'en', 'frontend', 'Length must be between {min} and {max}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.lengthRule' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.maxLengthRule', 'en', 'frontend', 'Length must not exceed {max}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.maxLengthRule' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'common.invalidFormat', 'en', 'frontend', 'Invalid format' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'common.invalidFormat' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'route.dashboard', 'zh-CN', 'frontend', '首页' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'route.dashboard' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'route.user', 'zh-CN', 'frontend', '用户管理' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'route.user' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'route.role', 'zh-CN', 'frontend', '角色管理' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'route.role' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'route.menu', 'zh-CN', 'frontend', '菜单管理' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'route.menu' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'route.unit', 'zh-CN', 'frontend', '单位管理' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'route.unit' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'route.config', 'zh-CN', 'frontend', '系统配置' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'route.config' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'route.app', 'zh-CN', 'frontend', '外部应用' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'route.app' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'route.audit', 'zh-CN', 'frontend', '审计日志' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'route.audit' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'route.session', 'zh-CN', 'frontend', '在线会话' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'route.session' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'route.i18n', 'zh-CN', 'frontend', '国际化管理' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'route.i18n' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'route.dashboard', 'en', 'frontend', 'Dashboard' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'route.dashboard' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'route.user', 'en', 'frontend', 'User Management' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'route.user' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'route.role', 'en', 'frontend', 'Role Management' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'route.role' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'route.menu', 'en', 'frontend', 'Menu Management' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'route.menu' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'route.unit', 'en', 'frontend', 'Unit Management' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'route.unit' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'route.config', 'en', 'frontend', 'System Config' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'route.config' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'route.app', 'en', 'frontend', 'Applications' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'route.app' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'route.audit', 'en', 'frontend', 'Audit Log' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'route.audit' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'route.session', 'en', 'frontend', 'Active Sessions' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'route.session' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'route.i18n', 'en', 'frontend', 'Internationalization' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'route.i18n' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.passwordMethod', 'zh-CN', 'frontend', '账号密码' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.passwordMethod' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.username', 'zh-CN', 'frontend', '用户名' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.username' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.password', 'zh-CN', 'frontend', '密码' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.password' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.captchaCode', 'zh-CN', 'frontend', '验证码' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.captchaCode' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.usernamePlaceholder', 'zh-CN', 'frontend', '请输入用户名' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.usernamePlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.passwordPlaceholder', 'zh-CN', 'frontend', '请输入密码' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.passwordPlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.captchaPlaceholder', 'zh-CN', 'frontend', '请输入验证码' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.captchaPlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.usernameRequired', 'zh-CN', 'frontend', '用户名不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.usernameRequired' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.usernameTooLong', 'zh-CN', 'frontend', '用户名长度不能超过32' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.usernameTooLong' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.passwordRequired', 'zh-CN', 'frontend', '密码不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.passwordRequired' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.passwordTooLong', 'zh-CN', 'frontend', '密码长度不能超过32' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.passwordTooLong' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.captchaTooLong', 'zh-CN', 'frontend', '验证码长度不能超过6' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.captchaTooLong' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.captchaLoadFailed', 'zh-CN', 'frontend', '验证码加载失败，请刷新重试' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.captchaLoadFailed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.accountLocked', 'zh-CN', 'frontend', '账号已锁定，联系管理员' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.accountLocked' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.captchaError', 'zh-CN', 'frontend', '验证码错误' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.captchaError' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.requestError', 'zh-CN', 'frontend', '请求参数错误' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.requestError' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.invalidCredentials', 'zh-CN', 'frontend', '用户名或密码错误' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.invalidCredentials' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.loginFailed', 'zh-CN', 'frontend', '登录失败，请稍后重试' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.loginFailed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.loginSuccess', 'zh-CN', 'frontend', '登录成功' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.loginSuccess' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.subtitle', 'zh-CN', 'frontend', '模块化开发平台' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.subtitle' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.featureModules', 'zh-CN', 'frontend', '用户、角色、权限、通知的模块化管理' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.featureModules' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.featureSecurity', 'zh-CN', 'frontend', 'JWT 认证与 OAuth2 授权服务' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.featureSecurity' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.featureAudit', 'zh-CN', 'frontend', '全链路操作审计与实时推送' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.featureAudit' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.login', 'zh-CN', 'frontend', '登录' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.login' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.refreshCaptcha', 'zh-CN', 'frontend', '点击刷新验证码' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.refreshCaptcha' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.passwordMethod', 'en', 'frontend', 'Username & Password' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.passwordMethod' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.username', 'en', 'frontend', 'Username' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.username' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.password', 'en', 'frontend', 'Password' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.password' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.captchaCode', 'en', 'frontend', 'Verification Code' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.captchaCode' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.usernamePlaceholder', 'en', 'frontend', 'Enter username' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.usernamePlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.passwordPlaceholder', 'en', 'frontend', 'Enter password' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.passwordPlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.captchaPlaceholder', 'en', 'frontend', 'Enter verification code' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.captchaPlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.usernameRequired', 'en', 'frontend', 'Username cannot be empty' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.usernameRequired' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.usernameTooLong', 'en', 'frontend', 'Username length cannot exceed 32' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.usernameTooLong' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.passwordRequired', 'en', 'frontend', 'Password cannot be empty' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.passwordRequired' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.passwordTooLong', 'en', 'frontend', 'Password length cannot exceed 32' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.passwordTooLong' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.captchaTooLong', 'en', 'frontend', 'Verification code length cannot exceed 6' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.captchaTooLong' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.captchaLoadFailed', 'en', 'frontend', 'Failed to load verification code, please refresh and try again' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.captchaLoadFailed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.accountLocked', 'en', 'frontend', 'Account locked, please contact administrator' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.accountLocked' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.captchaError', 'en', 'frontend', 'Incorrect verification code' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.captchaError' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.requestError', 'en', 'frontend', 'Invalid request parameters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.requestError' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.invalidCredentials', 'en', 'frontend', 'Invalid username or password' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.invalidCredentials' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.loginFailed', 'en', 'frontend', 'Login failed, please try again later' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.loginFailed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.loginSuccess', 'en', 'frontend', 'Login successful' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.loginSuccess' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.subtitle', 'en', 'frontend', 'Modular Development Platform' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.subtitle' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.featureModules', 'en', 'frontend', 'Modular management of users, roles, permissions, and notifications' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.featureModules' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.featureSecurity', 'en', 'frontend', 'JWT authentication and OAuth2 authorization service' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.featureSecurity' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.featureAudit', 'en', 'frontend', 'Full-chain operational audit and real-time push notifications' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.featureAudit' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.login', 'en', 'frontend', 'Sign In' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.login' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'login.refreshCaptcha', 'en', 'frontend', 'Click to refresh verification code' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'login.refreshCaptcha' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.levelUrgent', 'zh-CN', 'frontend', '紧急' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.levelUrgent' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.levelImportant', 'zh-CN', 'frontend', '重要' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.levelImportant' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.levelNormal', 'zh-CN', 'frontend', '普通' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.levelNormal' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.messageCenter', 'zh-CN', 'frontend', '消息中心' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.messageCenter' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.unreadMessages', 'zh-CN', 'frontend', '未读消息' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.unreadMessages' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.noUnreadMessages', 'zh-CN', 'frontend', '暂无未读消息' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.noUnreadMessages' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.noTitle', 'zh-CN', 'frontend', '(无标题)' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.noTitle' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.viewAll', 'zh-CN', 'frontend', '查看全部' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.viewAll' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.logout', 'zh-CN', 'frontend', '退出登录' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.logout' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.urgentNotification', 'zh-CN', 'frontend', '紧急通知' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.urgentNotification' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.newMessage', 'zh-CN', 'frontend', '新消息' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.newMessage' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.handleLater', 'zh-CN', 'frontend', '稍后处理' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.handleLater' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.viewDetails', 'zh-CN', 'frontend', '查看详情' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.viewDetails' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.levelUrgent', 'en', 'frontend', 'Urgent' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.levelUrgent' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.levelImportant', 'en', 'frontend', 'Important' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.levelImportant' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.levelNormal', 'en', 'frontend', 'Normal' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.levelNormal' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.messageCenter', 'en', 'frontend', 'Message Center' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.messageCenter' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.unreadMessages', 'en', 'frontend', 'Unread Messages' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.unreadMessages' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.noUnreadMessages', 'en', 'frontend', 'No unread messages' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.noUnreadMessages' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.noTitle', 'en', 'frontend', '(No title)' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.noTitle' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.viewAll', 'en', 'frontend', 'View All' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.viewAll' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.logout', 'en', 'frontend', 'Logout' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.logout' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.urgentNotification', 'en', 'frontend', 'Urgent Notification' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.urgentNotification' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.newMessage', 'en', 'frontend', 'New Message' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.newMessage' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.handleLater', 'en', 'frontend', 'Handle Later' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.handleLater' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'layout.viewDetails', 'en', 'frontend', 'View Details' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'layout.viewDetails' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'dashboard.welcome', 'zh-CN', 'frontend', '欢迎回来' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'dashboard.welcome' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'dashboard.welcome', 'en', 'frontend', 'Welcome back' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'dashboard.welcome' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'notFound.message', 'zh-CN', 'frontend', '页面不存在' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'notFound.message' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'notFound.message', 'en', 'frontend', 'Page not found' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'notFound.message' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.auth.bad.credentials', 'zh-CN', 'frontend', '用户名或密码错误' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.auth.bad.credentials' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.user.disabled', 'zh-CN', 'frontend', '用户已被禁用' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.user.disabled' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.auth.captcha.required', 'zh-CN', 'frontend', '请输入验证码' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.auth.captcha.required' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.auth.captcha.invalid', 'zh-CN', 'frontend', '验证码错误或已过期' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.auth.captcha.invalid' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.auth.not.login', 'zh-CN', 'frontend', '未登录' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.auth.not.login' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.auth.method.unsupported', 'zh-CN', 'frontend', '不支持的登录方式' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.auth.method.unsupported' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.user.username.exists', 'zh-CN', 'frontend', '用户名已存在' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.user.username.exists' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.user.delete.forbidden', 'zh-CN', 'frontend', '无权删除数据权限范围外的用户' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.user.delete.forbidden' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.role.code.exists', 'zh-CN', 'frontend', '角色编码已存在' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.role.code.exists' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.menu.parent.self', 'zh-CN', 'frontend', '上级菜单不能是自己' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.menu.parent.self' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.menu.has.children', 'zh-CN', 'frontend', '存在子菜单，无法删除' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.menu.has.children' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.config.key.exists', 'zh-CN', 'frontend', '配置键已存在' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.config.key.exists' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.unit.code.exists', 'zh-CN', 'frontend', '单位编码已存在' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.unit.code.exists' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.unit.parent.self', 'zh-CN', 'frontend', '上级单位不能是自己' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.unit.parent.self' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.unit.has.children', 'zh-CN', 'frontend', '存在子单位，无法删除' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.unit.has.children' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.session.not.found', 'zh-CN', 'frontend', '会话不存在或已过期' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.session.not.found' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.session.revoke.forbidden', 'zh-CN', 'frontend', '无权撤销他人会话' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.session.revoke.forbidden' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.permission.denied', 'zh-CN', 'frontend', '无权限' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.permission.denied' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.scope.no.token', 'zh-CN', 'frontend', '未授权：缺少有效的 OAuth2 access_token' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.scope.no.token' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.scope.not.oauth2', 'zh-CN', 'frontend', '非 OAuth2 令牌' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.scope.not.oauth2' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.scope.missing', 'zh-CN', 'frontend', '缺少 OAuth2 scope' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.scope.missing' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.access.denied', 'zh-CN', 'frontend', '无权限访问' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.access.denied' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.system', 'zh-CN', 'frontend', '系统内部错误' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.system' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.ldap.empty.credentials', 'zh-CN', 'frontend', '用户名和密码不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.ldap.empty.credentials' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.ldap.auth.failed', 'zh-CN', 'frontend', 'LDAP 认证失败' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.ldap.auth.failed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.ldap.service.unavailable', 'zh-CN', 'frontend', 'LDAP 服务不可用' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.ldap.service.unavailable' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.account.locked', 'zh-CN', 'frontend', '账号已锁定，请联系管理员或稍后重试' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.account.locked' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.auth.bad.credentials', 'en', 'frontend', 'Invalid username or password' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.auth.bad.credentials' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.user.disabled', 'en', 'frontend', 'User has been disabled' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.user.disabled' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.auth.captcha.required', 'en', 'frontend', 'Captcha is required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.auth.captcha.required' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.auth.captcha.invalid', 'en', 'frontend', 'Invalid or expired captcha' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.auth.captcha.invalid' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.auth.not.login', 'en', 'frontend', 'Not logged in' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.auth.not.login' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.auth.method.unsupported', 'en', 'frontend', 'Unsupported login method' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.auth.method.unsupported' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.user.username.exists', 'en', 'frontend', 'Username already exists' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.user.username.exists' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.user.delete.forbidden', 'en', 'frontend', 'Forbidden: cannot delete users outside your data scope' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.user.delete.forbidden' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.role.code.exists', 'en', 'frontend', 'Role code already exists' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.role.code.exists' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.menu.parent.self', 'en', 'frontend', 'Parent menu cannot be itself' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.menu.parent.self' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.menu.has.children', 'en', 'frontend', 'Has child menus, cannot delete' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.menu.has.children' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.config.key.exists', 'en', 'frontend', 'Config key already exists' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.config.key.exists' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.unit.code.exists', 'en', 'frontend', 'Unit code already exists' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.unit.code.exists' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.unit.parent.self', 'en', 'frontend', 'Parent unit cannot be itself' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.unit.parent.self' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.unit.has.children', 'en', 'frontend', 'Has child units, cannot delete' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.unit.has.children' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.session.not.found', 'en', 'frontend', 'Session not found or expired' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.session.not.found' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.session.revoke.forbidden', 'en', 'frontend', 'Forbidden: cannot revoke other users'' sessions' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.session.revoke.forbidden' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.permission.denied', 'en', 'frontend', 'Permission denied' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.permission.denied' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.scope.no.token', 'en', 'frontend', 'Unauthorized: missing valid OAuth2 access_token' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.scope.no.token' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.scope.not.oauth2', 'en', 'frontend', 'Not an OAuth2 token' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.scope.not.oauth2' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.scope.missing', 'en', 'frontend', 'Missing OAuth2 scope' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.scope.missing' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.access.denied', 'en', 'frontend', 'Access denied' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.access.denied' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.system', 'en', 'frontend', 'Internal server error' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.system' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.ldap.empty.credentials', 'en', 'frontend', 'Username and password must not be empty' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.ldap.empty.credentials' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.ldap.auth.failed', 'en', 'frontend', 'LDAP authentication failed' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.ldap.auth.failed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.ldap.service.unavailable', 'en', 'frontend', 'LDAP service unavailable' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.ldap.service.unavailable' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'error.error.account.locked', 'en', 'frontend', 'Account is locked, please contact the administrator or try again later' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'error.error.account.locked' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.title', 'zh-CN', 'i18n', '国际化管理' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.title' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.filter.locale', 'zh-CN', 'i18n', '语言' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.filter.locale' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.filter.module', 'zh-CN', 'i18n', '模块' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.filter.module' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.filter.keyLike', 'zh-CN', 'i18n', 'Key 搜索' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.filter.keyLike' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.filter.placeholder', 'zh-CN', 'i18n', '输入 key 关键字' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.filter.placeholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.column.key', 'zh-CN', 'i18n', 'Message Key' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.column.key' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.column.module', 'zh-CN', 'i18n', '模块' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.column.module' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.column.description', 'zh-CN', 'i18n', '说明' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.column.description' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.column.value', 'zh-CN', 'i18n', '展示文本' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.column.value' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.column.updatedAt', 'zh-CN', 'i18n', '更新时间' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.column.updatedAt' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.column.actions', 'zh-CN', 'i18n', '操作' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.column.actions' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.action.edit', 'zh-CN', 'i18n', '编辑' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.action.edit' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.action.import', 'zh-CN', 'i18n', '导入' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.action.import' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.action.exportJson', 'zh-CN', 'i18n', '导出 JSON' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.action.exportJson' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.action.exportXlsx', 'zh-CN', 'i18n', '导出 Excel' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.action.exportXlsx' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.action.refresh', 'zh-CN', 'i18n', '刷新' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.action.refresh' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.editModal.title', 'zh-CN', 'i18n', '编辑展示文本' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.editModal.title' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.editModal.value', 'zh-CN', 'i18n', '展示文本' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.editModal.value' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.editModal.key', 'zh-CN', 'i18n', 'Message Key' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.editModal.key' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.editModal.module', 'zh-CN', 'i18n', '所属模块' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.editModal.module' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.editModal.valueRequired', 'zh-CN', 'i18n', '请输入展示文本' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.editModal.valueRequired' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.editModal.valueMaxLength', 'zh-CN', 'i18n', '展示文本长度不能超过 5000 个字符' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.editModal.valueMaxLength' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.importModal.title', 'zh-CN', 'i18n', '导入翻译' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.importModal.title' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.importModal.locale', 'zh-CN', 'i18n', '目标语言' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.importModal.locale' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.importModal.file', 'zh-CN', 'i18n', '选择文件' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.importModal.file' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.importModal.fileRequired', 'zh-CN', 'i18n', '请选择文件' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.importModal.fileRequired' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.importModal.fileHint', 'zh-CN', 'i18n', '支持 .json 和 .xlsx 格式' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.importModal.fileHint' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.message.updateSuccess', 'zh-CN', 'i18n', '更新成功' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.message.updateSuccess' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.message.updateFailed', 'zh-CN', 'i18n', '更新失败' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.message.updateFailed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.message.importSuccess', 'zh-CN', 'i18n', '成功导入 {count} 条' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.message.importSuccess' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.message.importFailed', 'zh-CN', 'i18n', '导入失败' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.message.importFailed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.message.exportSuccess', 'zh-CN', 'i18n', '导出成功' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.message.exportSuccess' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.message.exportFailed', 'zh-CN', 'i18n', '导出失败' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.message.exportFailed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.message.loadFailed', 'zh-CN', 'i18n', '加载失败' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.message.loadFailed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.title', 'en', 'i18n', 'Internationalization' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.title' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.filter.locale', 'en', 'i18n', 'Locale' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.filter.locale' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.filter.module', 'en', 'i18n', 'Module' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.filter.module' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.filter.keyLike', 'en', 'i18n', 'Key Search' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.filter.keyLike' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.filter.placeholder', 'en', 'i18n', 'Enter key keyword' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.filter.placeholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.column.key', 'en', 'i18n', 'Message Key' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.column.key' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.column.module', 'en', 'i18n', 'Module' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.column.module' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.column.description', 'en', 'i18n', 'Description' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.column.description' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.column.value', 'en', 'i18n', 'Display Text' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.column.value' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.column.updatedAt', 'en', 'i18n', 'Updated At' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.column.updatedAt' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.column.actions', 'en', 'i18n', 'Actions' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.column.actions' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.action.edit', 'en', 'i18n', 'Edit' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.action.edit' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.action.import', 'en', 'i18n', 'Import' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.action.import' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.action.exportJson', 'en', 'i18n', 'Export JSON' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.action.exportJson' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.action.exportXlsx', 'en', 'i18n', 'Export Excel' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.action.exportXlsx' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.action.refresh', 'en', 'i18n', 'Refresh' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.action.refresh' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.editModal.title', 'en', 'i18n', 'Edit Translation' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.editModal.title' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.editModal.value', 'en', 'i18n', 'Display Text' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.editModal.value' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.editModal.key', 'en', 'i18n', 'Message Key' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.editModal.key' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.editModal.module', 'en', 'i18n', 'Module' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.editModal.module' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.editModal.valueRequired', 'en', 'i18n', 'Please enter the display text' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.editModal.valueRequired' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.editModal.valueMaxLength', 'en', 'i18n', 'Display text cannot exceed 5000 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.editModal.valueMaxLength' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.importModal.title', 'en', 'i18n', 'Import Translations' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.importModal.title' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.importModal.locale', 'en', 'i18n', 'Target Locale' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.importModal.locale' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.importModal.file', 'en', 'i18n', 'Select File' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.importModal.file' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.importModal.fileRequired', 'en', 'i18n', 'Please select a file' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.importModal.fileRequired' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.importModal.fileHint', 'en', 'i18n', 'Supports .json and .xlsx formats' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.importModal.fileHint' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.message.updateSuccess', 'en', 'i18n', 'Updated successfully' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.message.updateSuccess' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.message.updateFailed', 'en', 'i18n', 'Update failed' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.message.updateFailed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.message.importSuccess', 'en', 'i18n', 'Imported {count} entries' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.message.importSuccess' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.message.importFailed', 'en', 'i18n', 'Import failed' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.message.importFailed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.message.exportSuccess', 'en', 'i18n', 'Exported successfully' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.message.exportSuccess' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.message.exportFailed', 'en', 'i18n', 'Export failed' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.message.exportFailed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.message.loadFailed', 'en', 'i18n', 'Load failed' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.message.loadFailed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.username', 'zh-CN', 'sys', '用户名' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.username' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.realName', 'zh-CN', 'sys', '姓名' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.realName' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.email', 'zh-CN', 'sys', '邮箱' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.email' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.phone', 'zh-CN', 'sys', '电话' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.phone' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.unit', 'zh-CN', 'sys', '单位' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.unit' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.role', 'zh-CN', 'sys', '角色' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.role' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.createTime', 'zh-CN', 'sys', '创建时间' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.createTime' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.locked', 'zh-CN', 'sys', '锁定' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.locked' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.normal', 'zh-CN', 'sys', '正常' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.normal' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.disabled', 'zh-CN', 'sys', '禁用' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.disabled' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.password', 'zh-CN', 'sys', '密码' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.password' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.confirmPassword', 'zh-CN', 'sys', '确认密码' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.confirmPassword' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.searchPlaceholder', 'zh-CN', 'sys', '搜索用户名/姓名/电话' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.searchPlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.usernamePlaceholder', 'zh-CN', 'sys', '请输入用户名' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.usernamePlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.passwordPlaceholder', 'zh-CN', 'sys', '请输入密码' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.passwordPlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.realNamePlaceholder', 'zh-CN', 'sys', '请输入姓名' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.realNamePlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.emailPlaceholder', 'zh-CN', 'sys', '请输入邮箱' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.emailPlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.phonePlaceholder', 'zh-CN', 'sys', '请输入电话' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.phonePlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.unitPlaceholder', 'zh-CN', 'sys', '请选择单位' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.unitPlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.rolePlaceholder', 'zh-CN', 'sys', '请选择角色' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.rolePlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.addUserTitle', 'zh-CN', 'sys', '新增用户' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.addUserTitle' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.editUserTitle', 'zh-CN', 'sys', '编辑用户' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.editUserTitle' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.edit', 'zh-CN', 'sys', '编辑' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.edit' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.unlock', 'zh-CN', 'sys', '解锁' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.unlock' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.resetPassword', 'zh-CN', 'sys', '重置密码' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.resetPassword' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.confirmResetPassword', 'zh-CN', 'sys', '确认重置密码为 User@123456？' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.confirmResetPassword' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.unlockSuccess', 'zh-CN', 'sys', '解锁成功' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.unlockSuccess' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.unlockFailed', 'zh-CN', 'sys', '解锁失败' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.unlockFailed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.passwordReset', 'zh-CN', 'sys', '密码已重置为 User@123456' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.passwordReset' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.resetFailed', 'zh-CN', 'sys', '重置失败' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.resetFailed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.usernameRequired', 'zh-CN', 'sys', '用户名不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.usernameRequired' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.usernameLength', 'zh-CN', 'sys', '用户名长度需在3-32之间' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.usernameLength' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.usernamePattern', 'zh-CN', 'sys', '用户名只能包含字母、数字、下划线' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.usernamePattern' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.passwordRequired', 'zh-CN', 'sys', '密码不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.passwordRequired' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.passwordLength', 'zh-CN', 'sys', '密码长度需在6-32之间' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.passwordLength' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.realNameLength', 'zh-CN', 'sys', '姓名长度不能超过50' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.realNameLength' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.phoneLength', 'zh-CN', 'sys', '手机号长度不能超过20' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.phoneLength' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.phonePattern', 'zh-CN', 'sys', '手机号格式不正确' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.phonePattern' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.username', 'en', 'sys', 'Username' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.username' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.realName', 'en', 'sys', 'Real Name' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.realName' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.email', 'en', 'sys', 'Email' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.email' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.phone', 'en', 'sys', 'Phone' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.phone' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.unit', 'en', 'sys', 'Unit' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.unit' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.role', 'en', 'sys', 'Role' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.role' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.createTime', 'en', 'sys', 'Created At' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.createTime' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.locked', 'en', 'sys', 'Locked' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.locked' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.normal', 'en', 'sys', 'Normal' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.normal' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.disabled', 'en', 'sys', 'Disabled' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.disabled' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.password', 'en', 'sys', 'Password' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.password' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.confirmPassword', 'en', 'sys', 'Confirm Password' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.confirmPassword' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.searchPlaceholder', 'en', 'sys', 'Search username/name/phone' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.searchPlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.usernamePlaceholder', 'en', 'sys', 'Please enter username' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.usernamePlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.passwordPlaceholder', 'en', 'sys', 'Please enter password' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.passwordPlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.realNamePlaceholder', 'en', 'sys', 'Please enter real name' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.realNamePlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.emailPlaceholder', 'en', 'sys', 'Please enter email' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.emailPlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.phonePlaceholder', 'en', 'sys', 'Please enter phone' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.phonePlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.unitPlaceholder', 'en', 'sys', 'Please select unit' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.unitPlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.rolePlaceholder', 'en', 'sys', 'Please select role' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.rolePlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.addUserTitle', 'en', 'sys', 'Add User' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.addUserTitle' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.editUserTitle', 'en', 'sys', 'Edit User' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.editUserTitle' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.edit', 'en', 'sys', 'Edit' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.edit' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.unlock', 'en', 'sys', 'Unlock' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.unlock' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.resetPassword', 'en', 'sys', 'Reset Password' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.resetPassword' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.confirmResetPassword', 'en', 'sys', 'Confirm reset password to User@123456?' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.confirmResetPassword' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.unlockSuccess', 'en', 'sys', 'Unlock successful' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.unlockSuccess' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.unlockFailed', 'en', 'sys', 'Unlock failed' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.unlockFailed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.passwordReset', 'en', 'sys', 'Password has been reset to User@123456' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.passwordReset' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.resetFailed', 'en', 'sys', 'Reset failed' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.resetFailed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.usernameRequired', 'en', 'sys', 'Username cannot be empty' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.usernameRequired' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.usernameLength', 'en', 'sys', 'Username length must be between 3-32' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.usernameLength' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.usernamePattern', 'en', 'sys', 'Username can only contain letters, numbers, and underscores' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.usernamePattern' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.passwordRequired', 'en', 'sys', 'Password cannot be empty' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.passwordRequired' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.passwordLength', 'en', 'sys', 'Password length must be between 6-32' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.passwordLength' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.realNameLength', 'en', 'sys', 'Real name length cannot exceed 50' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.realNameLength' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.phoneLength', 'en', 'sys', 'Phone length cannot exceed 20' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.phoneLength' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.user.phonePattern', 'en', 'sys', 'Phone format is incorrect' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.user.phonePattern' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.roleCode', 'zh-CN', 'sys', '角色编码' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.roleCode' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.roleName', 'zh-CN', 'sys', '角色名称' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.roleName' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.dataScope', 'zh-CN', 'sys', '数据范围' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.dataScope' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.remark', 'zh-CN', 'sys', '备注' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.remark' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.scopeAll', 'zh-CN', 'sys', '全部数据' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.scopeAll' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.scopeUnit', 'zh-CN', 'sys', '本单位' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.scopeUnit' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.scopeUnitBelow', 'zh-CN', 'sys', '本单位及下属' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.scopeUnitBelow' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.scopeSelf', 'zh-CN', 'sys', '仅本人' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.scopeSelf' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.scopeCustom', 'zh-CN', 'sys', '自定义' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.scopeCustom' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.customUnit', 'zh-CN', 'sys', '自定义单位' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.customUnit' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.addRole', 'zh-CN', 'sys', '新增角色' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.addRole' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.editRole', 'zh-CN', 'sys', '编辑角色' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.editRole' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.assignPermissions', 'zh-CN', 'sys', '分配权限' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.assignPermissions' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.roleCodePlaceholder', 'zh-CN', 'sys', '如 admin' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.roleCodePlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.roleNamePlaceholder', 'zh-CN', 'sys', '如 超级管理员' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.roleNamePlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.remarkPlaceholder', 'zh-CN', 'sys', '备注' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.remarkPlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.permissions', 'zh-CN', 'sys', '权限' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.permissions' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.edit', 'zh-CN', 'sys', '编辑' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.edit' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.delete', 'zh-CN', 'sys', '删除' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.delete' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.savePermissions', 'zh-CN', 'sys', '保存' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.savePermissions' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.enabled', 'zh-CN', 'sys', '启用' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.enabled' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.customScopeFailed', 'zh-CN', 'sys', '自定义数据范围保存失败：后端暂未提供该端点（见 T24 limitation）' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.customScopeFailed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.assignPermissionsSuccess', 'zh-CN', 'sys', '权限分配成功' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.assignPermissionsSuccess' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.roleCodeRequired', 'zh-CN', 'sys', '角色编码不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.roleCodeRequired' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.roleCodeLength', 'zh-CN', 'sys', '角色编码长度需在3-50之间' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.roleCodeLength' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.roleCodePattern', 'zh-CN', 'sys', '角色编码只能包含字母、数字、下划线' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.roleCodePattern' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.roleNameRequired', 'zh-CN', 'sys', '角色名称不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.roleNameRequired' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.roleNameLength', 'zh-CN', 'sys', '角色名称长度不能超过100' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.roleNameLength' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.dataScopeRequired', 'zh-CN', 'sys', '数据范围不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.dataScopeRequired' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.remarkLength', 'zh-CN', 'sys', '备注长度不能超过200' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.remarkLength' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.roleCode', 'en', 'sys', 'Role Code' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.roleCode' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.roleName', 'en', 'sys', 'Role Name' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.roleName' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.dataScope', 'en', 'sys', 'Data Scope' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.dataScope' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.remark', 'en', 'sys', 'Remark' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.remark' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.scopeAll', 'en', 'sys', 'All Data' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.scopeAll' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.scopeUnit', 'en', 'sys', 'This Unit' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.scopeUnit' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.scopeUnitBelow', 'en', 'sys', 'This Unit and Below' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.scopeUnitBelow' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.scopeSelf', 'en', 'sys', 'Self Only' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.scopeSelf' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.scopeCustom', 'en', 'sys', 'Custom' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.scopeCustom' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.customUnit', 'en', 'sys', 'Custom Unit' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.customUnit' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.addRole', 'en', 'sys', 'Add Role' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.addRole' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.editRole', 'en', 'sys', 'Edit Role' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.editRole' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.assignPermissions', 'en', 'sys', 'Assign Permissions' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.assignPermissions' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.roleCodePlaceholder', 'en', 'sys', 'e.g., admin' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.roleCodePlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.roleNamePlaceholder', 'en', 'sys', 'e.g., Super Admin' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.roleNamePlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.remarkPlaceholder', 'en', 'sys', 'Remark' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.remarkPlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.permissions', 'en', 'sys', 'Permissions' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.permissions' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.edit', 'en', 'sys', 'Edit' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.edit' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.delete', 'en', 'sys', 'Delete' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.delete' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.savePermissions', 'en', 'sys', 'Save' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.savePermissions' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.enabled', 'en', 'sys', 'Enabled' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.enabled' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.customScopeFailed', 'en', 'sys', 'Custom data scope save failed: Backend endpoint not yet available (see T24 limitation)' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.customScopeFailed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.assignPermissionsSuccess', 'en', 'sys', 'Permission assignment successful' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.assignPermissionsSuccess' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.roleCodeRequired', 'en', 'sys', 'Role code cannot be empty' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.roleCodeRequired' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.roleCodeLength', 'en', 'sys', 'Role code length must be between 3-50' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.roleCodeLength' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.roleCodePattern', 'en', 'sys', 'Role code can only contain letters, numbers, and underscores' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.roleCodePattern' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.roleNameRequired', 'en', 'sys', 'Role name cannot be empty' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.roleNameRequired' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.roleNameLength', 'en', 'sys', 'Role name length cannot exceed 100' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.roleNameLength' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.dataScopeRequired', 'en', 'sys', 'Data scope cannot be empty' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.dataScopeRequired' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.role.remarkLength', 'en', 'sys', 'Remark length cannot exceed 200' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.role.remarkLength' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.noMenuData', 'zh-CN', 'sys', '暂无菜单数据' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.noMenuData' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.menuName', 'zh-CN', 'sys', '菜单名称' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.menuName' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.icon', 'zh-CN', 'sys', '图标' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.icon' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.sort', 'zh-CN', 'sys', '排序' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.sort' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.isShow', 'zh-CN', 'sys', '是否显示' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.isShow' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.status', 'zh-CN', 'sys', '状态' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.status' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.typeDirectory', 'zh-CN', 'sys', '目录' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.typeDirectory' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.typePage', 'zh-CN', 'sys', '页面' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.typePage' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.typeButton', 'zh-CN', 'sys', '按钮' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.typeButton' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.iconSettings', 'zh-CN', 'sys', '设置' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.iconSettings' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.iconUser', 'zh-CN', 'sys', '用户' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.iconUser' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.iconRole', 'zh-CN', 'sys', '角色权限' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.iconRole' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.iconMenu', 'zh-CN', 'sys', '菜单' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.iconMenu' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.iconBuilding', 'zh-CN', 'sys', '办公楼' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.iconBuilding' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.iconTools', 'zh-CN', 'sys', '工具' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.iconTools' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.iconGlobe', 'zh-CN', 'sys', '地球' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.iconGlobe' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.iconDocument', 'zh-CN', 'sys', '文档' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.iconDocument' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.iconApps', 'zh-CN', 'sys', '应用' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.iconApps' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.menuNamePlaceholder', 'zh-CN', 'sys', '菜单名称' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.menuNamePlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.iconPlaceholder', 'zh-CN', 'sys', '选择图标' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.iconPlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.sortPlaceholder', 'zh-CN', 'sys', '0' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.sortPlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.editMenu', 'zh-CN', 'sys', '编辑菜单' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.editMenu' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.edit', 'zh-CN', 'sys', '编辑' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.edit' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.menuNameRequired', 'zh-CN', 'sys', '菜单名称不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.menuNameRequired' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.menuNameLength', 'zh-CN', 'sys', '菜单名称长度不能超过50' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.menuNameLength' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.sortPattern', 'zh-CN', 'sys', '排序值必须是非负整数' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.sortPattern' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.noMenuData', 'en', 'sys', 'No menu data available' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.noMenuData' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.menuName', 'en', 'sys', 'Menu Name' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.menuName' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.icon', 'en', 'sys', 'Icon' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.icon' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.sort', 'en', 'sys', 'Sort' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.sort' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.isShow', 'en', 'sys', 'Show' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.isShow' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.status', 'en', 'sys', 'Status' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.status' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.typeDirectory', 'en', 'sys', 'Directory' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.typeDirectory' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.typePage', 'en', 'sys', 'Page' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.typePage' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.typeButton', 'en', 'sys', 'Button' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.typeButton' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.iconSettings', 'en', 'sys', 'Settings' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.iconSettings' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.iconUser', 'en', 'sys', 'User' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.iconUser' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.iconRole', 'en', 'sys', 'Role Permissions' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.iconRole' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.iconMenu', 'en', 'sys', 'Menu' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.iconMenu' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.iconBuilding', 'en', 'sys', 'Office Building' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.iconBuilding' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.iconTools', 'en', 'sys', 'Tools' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.iconTools' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.iconGlobe', 'en', 'sys', 'Globe' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.iconGlobe' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.iconDocument', 'en', 'sys', 'Document' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.iconDocument' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.iconApps', 'en', 'sys', 'Apps' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.iconApps' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.menuNamePlaceholder', 'en', 'sys', 'Menu name' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.menuNamePlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.iconPlaceholder', 'en', 'sys', 'Select icon' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.iconPlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.sortPlaceholder', 'en', 'sys', '0' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.sortPlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.editMenu', 'en', 'sys', 'Edit Menu' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.editMenu' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.edit', 'en', 'sys', 'Edit' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.edit' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.menuNameRequired', 'en', 'sys', 'Menu name cannot be empty' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.menuNameRequired' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.menuNameLength', 'en', 'sys', 'Menu name length cannot exceed 50' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.menuNameLength' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.sortPattern', 'en', 'sys', 'Sort value must be a non-negative integer' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.sortPattern' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.noUnitData', 'zh-CN', 'sys', '暂无单位数据' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.noUnitData' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.parentUnit', 'zh-CN', 'sys', '上级单位' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.parentUnit' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.unitCode', 'zh-CN', 'sys', '单位编码' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.unitCode' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.unitName', 'zh-CN', 'sys', '单位名称' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.unitName' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.sort', 'zh-CN', 'sys', '排序' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.sort' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.status', 'zh-CN', 'sys', '状态' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.status' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.remark', 'zh-CN', 'sys', '备注' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.remark' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.normal', 'zh-CN', 'sys', '正常' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.normal' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.disabled', 'zh-CN', 'sys', '禁用' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.disabled' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.topUnit', 'zh-CN', 'sys', '顶级单位' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.topUnit' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.unitCodePlaceholder', 'zh-CN', 'sys', '如 HQ' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.unitCodePlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.unitNamePlaceholder', 'zh-CN', 'sys', '如 总部' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.unitNamePlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.sortPlaceholder', 'zh-CN', 'sys', '0' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.sortPlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.addUnit', 'zh-CN', 'sys', '新增单位' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.addUnit' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.editUnit', 'zh-CN', 'sys', '编辑单位' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.editUnit' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.add', 'zh-CN', 'sys', '新增' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.add' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.edit', 'zh-CN', 'sys', '编辑' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.edit' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.delete', 'zh-CN', 'sys', '删除' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.delete' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.unitCodeRequired', 'zh-CN', 'sys', '单位编码不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.unitCodeRequired' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.unitCodeLength', 'zh-CN', 'sys', '单位编码长度需在3-50之间' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.unitCodeLength' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.unitCodePattern', 'zh-CN', 'sys', '单位编码只能包含字母、数字、下划线' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.unitCodePattern' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.unitNameRequired', 'zh-CN', 'sys', '单位名称不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.unitNameRequired' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.unitNameLength', 'zh-CN', 'sys', '单位名称长度不能超过100' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.unitNameLength' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.sortPattern', 'zh-CN', 'sys', '排序值必须是非负整数' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.sortPattern' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.remarkLength', 'zh-CN', 'sys', '备注长度不能超过200' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.remarkLength' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.noUnitData', 'en', 'sys', 'No unit data available' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.noUnitData' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.parentUnit', 'en', 'sys', 'Parent Unit' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.parentUnit' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.unitCode', 'en', 'sys', 'Unit Code' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.unitCode' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.unitName', 'en', 'sys', 'Unit Name' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.unitName' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.sort', 'en', 'sys', 'Sort' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.sort' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.status', 'en', 'sys', 'Status' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.status' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.remark', 'en', 'sys', 'Remark' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.remark' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.normal', 'en', 'sys', 'Normal' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.normal' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.disabled', 'en', 'sys', 'Disabled' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.disabled' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.topUnit', 'en', 'sys', 'Top Level Unit' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.topUnit' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.unitCodePlaceholder', 'en', 'sys', 'e.g., HQ' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.unitCodePlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.unitNamePlaceholder', 'en', 'sys', 'e.g., Headquarters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.unitNamePlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.sortPlaceholder', 'en', 'sys', '0' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.sortPlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.addUnit', 'en', 'sys', 'Add Unit' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.addUnit' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.editUnit', 'en', 'sys', 'Edit Unit' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.editUnit' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.add', 'en', 'sys', 'Add' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.add' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.edit', 'en', 'sys', 'Edit' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.edit' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.delete', 'en', 'sys', 'Delete' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.delete' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.unitCodeRequired', 'en', 'sys', 'Unit code cannot be empty' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.unitCodeRequired' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.unitCodeLength', 'en', 'sys', 'Unit code length must be between 3-50' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.unitCodeLength' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.unitCodePattern', 'en', 'sys', 'Unit code can only contain letters, numbers, and underscores' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.unitCodePattern' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.unitNameRequired', 'en', 'sys', 'Unit name cannot be empty' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.unitNameRequired' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.unitNameLength', 'en', 'sys', 'Unit name length cannot exceed 100' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.unitNameLength' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.sortPattern', 'en', 'sys', 'Sort value must be a non-negative integer' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.sortPattern' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.unit.remarkLength', 'en', 'sys', 'Remark length cannot exceed 200' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.unit.remarkLength' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.configKey', 'zh-CN', 'sys', '配置键' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.configKey' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.configValue', 'zh-CN', 'sys', '配置值' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.configValue' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.configType', 'zh-CN', 'sys', '类型' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.configType' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.category', 'zh-CN', 'sys', '分类' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.category' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.description', 'zh-CN', 'sys', '描述' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.description' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.typeString', 'zh-CN', 'sys', '字符串' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.typeString' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.typeNumber', 'zh-CN', 'sys', '数字' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.typeNumber' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.typeBoolean', 'zh-CN', 'sys', '布尔' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.typeBoolean' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.typeJson', 'zh-CN', 'sys', 'JSON' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.typeJson' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.key', 'zh-CN', 'sys', '配置键' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.key' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.value', 'zh-CN', 'sys', '配置值' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.value' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.type', 'zh-CN', 'sys', '类型' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.type' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.addConfig', 'zh-CN', 'sys', '新增配置' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.addConfig' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.editConfig', 'zh-CN', 'sys', '编辑配置' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.editConfig' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.configKeyPlaceholder', 'zh-CN', 'sys', '如：sys.password.min-length' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.configKeyPlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.configValuePlaceholder', 'zh-CN', 'sys', '配置值' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.configValuePlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.categoryPlaceholder', 'zh-CN', 'sys', 'default' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.categoryPlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.descriptionPlaceholder', 'zh-CN', 'sys', '配置描述' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.descriptionPlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.configKeyInvalid', 'zh-CN', 'sys', '配置键只能包含字母、数字、点、下划线、连字符' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.configKeyInvalid' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.configValueMaxLength', 'zh-CN', 'sys', '配置值长度不能超过2000' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.configValueMaxLength' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.descriptionMaxLength', 'zh-CN', 'sys', '描述长度不能超过500' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.descriptionMaxLength' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.categoryMaxLength', 'zh-CN', 'sys', '分类长度不能超过50' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.categoryMaxLength' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.configKey', 'en', 'sys', 'Config Key' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.configKey' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.configValue', 'en', 'sys', 'Config Value' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.configValue' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.configType', 'en', 'sys', 'Type' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.configType' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.category', 'en', 'sys', 'Category' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.category' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.description', 'en', 'sys', 'Description' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.description' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.typeString', 'en', 'sys', 'String' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.typeString' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.typeNumber', 'en', 'sys', 'Number' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.typeNumber' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.typeBoolean', 'en', 'sys', 'Boolean' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.typeBoolean' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.typeJson', 'en', 'sys', 'JSON' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.typeJson' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.key', 'en', 'sys', 'Config Key' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.key' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.value', 'en', 'sys', 'Config Value' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.value' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.type', 'en', 'sys', 'Type' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.type' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.addConfig', 'en', 'sys', 'Add Config' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.addConfig' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.editConfig', 'en', 'sys', 'Edit Config' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.editConfig' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.configKeyPlaceholder', 'en', 'sys', 'e.g., sys.password.min-length' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.configKeyPlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.configValuePlaceholder', 'en', 'sys', 'Config Value' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.configValuePlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.categoryPlaceholder', 'en', 'sys', 'default' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.categoryPlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.descriptionPlaceholder', 'en', 'sys', 'Config Description' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.descriptionPlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.configKeyInvalid', 'en', 'sys', 'Config key can only contain letters, numbers, dots, underscores, and hyphens' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.configKeyInvalid' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.configValueMaxLength', 'en', 'sys', 'Config value cannot exceed 2000 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.configValueMaxLength' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.descriptionMaxLength', 'en', 'sys', 'Description cannot exceed 500 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.descriptionMaxLength' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.config.categoryMaxLength', 'en', 'sys', 'Category cannot exceed 50 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.config.categoryMaxLength' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.allLevels', 'zh-CN', 'sys', '全部级别' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.allLevels' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.allStatus', 'zh-CN', 'sys', '全部状态' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.allStatus' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.levelUrgent', 'zh-CN', 'sys', '紧急' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.levelUrgent' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.levelImportant', 'zh-CN', 'sys', '重要' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.levelImportant' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.levelNormal', 'zh-CN', 'sys', '普通' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.levelNormal' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.unread', 'zh-CN', 'sys', '未读' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.unread' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.read', 'zh-CN', 'sys', '已读' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.read' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.title', 'zh-CN', 'sys', '标题' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.title' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.level', 'zh-CN', 'sys', '级别' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.level' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.businessType', 'zh-CN', 'sys', '业务类型' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.businessType' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.sentTime', 'zh-CN', 'sys', '发送时间' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.sentTime' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.status', 'zh-CN', 'sys', '状态' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.status' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.operation', 'zh-CN', 'sys', '操作' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.operation' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.markRead', 'zh-CN', 'sys', '标记已读' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.markRead' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.batchMarkRead', 'zh-CN', 'sys', '批量标记已读' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.batchMarkRead' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.searchTitle', 'zh-CN', 'sys', '搜索标题' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.searchTitle' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.noTitle', 'zh-CN', 'sys', '(无标题)' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.noTitle' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.dash', 'zh-CN', 'sys', '—' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.dash' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.loadFailed', 'zh-CN', 'sys', '加载失败' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.loadFailed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.markedAsRead', 'zh-CN', 'sys', '已标记为已读' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.markedAsRead' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.selectUnreadMessages', 'zh-CN', 'sys', '请选择未读消息' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.selectUnreadMessages' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.markedCountAsRead', 'zh-CN', 'sys', '已标记 {count} 条为已读' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.markedCountAsRead' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.allLevels', 'en', 'sys', 'All Levels' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.allLevels' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.allStatus', 'en', 'sys', 'All Status' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.allStatus' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.levelUrgent', 'en', 'sys', 'Urgent' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.levelUrgent' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.levelImportant', 'en', 'sys', 'Important' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.levelImportant' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.levelNormal', 'en', 'sys', 'Normal' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.levelNormal' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.unread', 'en', 'sys', 'Unread' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.unread' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.read', 'en', 'sys', 'Read' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.read' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.title', 'en', 'sys', 'Title' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.title' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.level', 'en', 'sys', 'Level' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.level' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.businessType', 'en', 'sys', 'Business Type' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.businessType' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.sentTime', 'en', 'sys', 'Sent Time' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.sentTime' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.status', 'en', 'sys', 'Status' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.status' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.operation', 'en', 'sys', 'Operation' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.operation' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.markRead', 'en', 'sys', 'Mark as Read' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.markRead' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.batchMarkRead', 'en', 'sys', 'Batch Mark as Read' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.batchMarkRead' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.searchTitle', 'en', 'sys', 'Search Title' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.searchTitle' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.noTitle', 'en', 'sys', '(No Title)' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.noTitle' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.dash', 'en', 'sys', '—' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.dash' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.loadFailed', 'en', 'sys', 'Failed to load' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.loadFailed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.markedAsRead', 'en', 'sys', 'Marked as read' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.markedAsRead' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.selectUnreadMessages', 'en', 'sys', 'Please select unread messages' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.selectUnreadMessages' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.message.markedCountAsRead', 'en', 'sys', 'Marked {count} messages as read' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.message.markedCountAsRead' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.tabMySessions', 'zh-CN', 'sys', '我的会话' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.tabMySessions' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.tabUserSessions', 'zh-CN', 'sys', '用户会话查询' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.tabUserSessions' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.device', 'zh-CN', 'sys', '设备' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.device' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.ip', 'zh-CN', 'sys', 'IP' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.ip' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.loginTime', 'zh-CN', 'sys', '登录时间' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.loginTime' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.expireTime', 'zh-CN', 'sys', '过期时间' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.expireTime' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.userAgent', 'zh-CN', 'sys', 'User-Agent' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.userAgent' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.operation', 'zh-CN', 'sys', '操作' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.operation' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.username', 'zh-CN', 'sys', '用户名' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.username' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.logout', 'zh-CN', 'sys', '下线' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.logout' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.forceLogout', 'zh-CN', 'sys', '强制下线' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.forceLogout' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.confirmLogout', 'zh-CN', 'sys', '确认下线该会话？' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.confirmLogout' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.confirmForceLogout', 'zh-CN', 'sys', '确认强制下线该会话？' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.confirmForceLogout' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.revoked', 'zh-CN', 'sys', '已撤销该会话' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.revoked' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.forceLoggedOut', 'zh-CN', 'sys', '已强制下线' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.forceLoggedOut' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.revokeFailed', 'zh-CN', 'sys', '撤销失败' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.revokeFailed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.forceLogoutFailed', 'zh-CN', 'sys', '强制下线失败' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.forceLogoutFailed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.loadFailed', 'zh-CN', 'sys', '加载会话失败' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.loadFailed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.searchUserPlaceholder', 'zh-CN', 'sys', '搜索用户名 / 姓名' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.searchUserPlaceholder' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.noActiveSessions', 'zh-CN', 'sys', '暂无活跃会话' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.noActiveSessions' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.userNoActiveSessions', 'zh-CN', 'sys', '该用户暂无活跃会话' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.userNoActiveSessions' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.selectUser', 'zh-CN', 'sys', '请先选择要查询的用户' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.selectUser' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.tabMySessions', 'en', 'sys', 'My Sessions' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.tabMySessions' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.tabUserSessions', 'en', 'sys', 'User Sessions Query' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.tabUserSessions' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.device', 'en', 'sys', 'Device' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.device' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.ip', 'en', 'sys', 'IP' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.ip' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.loginTime', 'en', 'sys', 'Login Time' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.loginTime' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.expireTime', 'en', 'sys', 'Expire Time' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.expireTime' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.userAgent', 'en', 'sys', 'User-Agent' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.userAgent' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.operation', 'en', 'sys', 'Operation' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.operation' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.username', 'en', 'sys', 'Username' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.username' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.logout', 'en', 'sys', 'Logout' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.logout' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.forceLogout', 'en', 'sys', 'Force Logout' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.forceLogout' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.confirmLogout', 'en', 'sys', 'Confirm logout this session?' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.confirmLogout' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.confirmForceLogout', 'en', 'sys', 'Confirm force logout this session?' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.confirmForceLogout' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.revoked', 'en', 'sys', 'Session revoked' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.revoked' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.forceLoggedOut', 'en', 'sys', 'Force logged out' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.forceLoggedOut' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.revokeFailed', 'en', 'sys', 'Revoke failed' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.revokeFailed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.forceLogoutFailed', 'en', 'sys', 'Force logout failed' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.forceLogoutFailed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.loadFailed', 'en', 'sys', 'Failed to load sessions' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.loadFailed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.searchUserPlaceholder', 'en', 'sys', 'Search Username / Name' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.searchUserPlaceholder' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.noActiveSessions', 'en', 'sys', 'No active sessions' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.noActiveSessions' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.userNoActiveSessions', 'en', 'sys', 'User has no active sessions' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.userNoActiveSessions' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.session.selectUser', 'en', 'sys', 'Please select a user to query' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.session.selectUser' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.actor', 'zh-CN', 'sys', '操作人' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.actor' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.actionType', 'zh-CN', 'sys', '操作类型' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.actionType' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.targetType', 'zh-CN', 'sys', '对象类型' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.targetType' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.targetId', 'zh-CN', 'sys', '对象 ID' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.targetId' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.ip', 'zh-CN', 'sys', 'IP' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.ip' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.device', 'zh-CN', 'sys', '设备' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.device' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.result', 'zh-CN', 'sys', '结果' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.result' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.time', 'zh-CN', 'sys', '时间' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.time' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.success', 'zh-CN', 'sys', '成功' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.success' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.fail', 'zh-CN', 'sys', '失败' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.fail' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.params', 'zh-CN', 'sys', '参数' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.params' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.failReason', 'zh-CN', 'sys', '失败原因' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.failReason' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.noDetails', 'zh-CN', 'sys', '无详细信息' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.noDetails' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.placeholders.actor', 'zh-CN', 'sys', '操作人' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.placeholders.actor' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.placeholders.actionType', 'zh-CN', 'sys', '操作类型' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.placeholders.actionType' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.placeholders.result', 'zh-CN', 'sys', '结果' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.placeholders.result' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.placeholders.targetType', 'zh-CN', 'sys', '对象类型' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.placeholders.targetType' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.actor', 'en', 'sys', 'Actor' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.actor' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.actionType', 'en', 'sys', 'Action Type' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.actionType' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.targetType', 'en', 'sys', 'Target Type' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.targetType' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.targetId', 'en', 'sys', 'Target ID' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.targetId' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.ip', 'en', 'sys', 'IP' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.ip' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.device', 'en', 'sys', 'Device' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.device' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.result', 'en', 'sys', 'Result' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.result' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.time', 'en', 'sys', 'Time' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.time' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.success', 'en', 'sys', 'Success' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.success' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.fail', 'en', 'sys', 'Fail' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.fail' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.params', 'en', 'sys', 'Parameters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.params' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.failReason', 'en', 'sys', 'Failure Reason' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.failReason' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.noDetails', 'en', 'sys', 'No Details' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.noDetails' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.placeholders.actor', 'en', 'sys', 'Actor' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.placeholders.actor' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.placeholders.actionType', 'en', 'sys', 'Action Type' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.placeholders.actionType' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.placeholders.result', 'en', 'sys', 'Result' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.placeholders.result' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.audit.placeholders.targetType', 'en', 'sys', 'Target Type' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.audit.placeholders.targetType' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.clientId', 'zh-CN', 'sys', 'Client ID' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.clientId' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.appName', 'zh-CN', 'sys', '应用名称' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.appName' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.grantType', 'zh-CN', 'sys', '授权类型' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.grantType' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.status', 'zh-CN', 'sys', '状态' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.status' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.createTime', 'zh-CN', 'sys', '创建时间' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.createTime' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.operation', 'zh-CN', 'sys', '操作' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.operation' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.enabled', 'zh-CN', 'sys', '启用' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.enabled' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.disabled', 'zh-CN', 'sys', '禁用' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.disabled' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.grantTypes.authCode', 'zh-CN', 'sys', 'authorization_code（授权码）' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.grantTypes.authCode' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.grantTypes.refreshToken', 'zh-CN', 'sys', 'refresh_token（刷新令牌）' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.grantTypes.refreshToken' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.grantTypes.clientCredentials', 'zh-CN', 'sys', 'client_credentials（客户端凭据）' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.grantTypes.clientCredentials' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.placeholders.search', 'zh-CN', 'sys', '搜索 Client ID / 应用名称' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.placeholders.search' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.placeholders.clientName', 'zh-CN', 'sys', '如：移动端 App' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.placeholders.clientName' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.placeholders.redirectUri', 'zh-CN', 'sys', 'https://example.com/callback' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.placeholders.redirectUri' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.placeholders.postLogoutRedirectUri', 'zh-CN', 'sys', 'https://example.com/post-logout' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.placeholders.postLogoutRedirectUri' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.placeholders.scopes', 'zh-CN', 'sys', '选择或输入 scope' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.placeholders.scopes' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.placeholders.grantTypes', 'zh-CN', 'sys', '选择授权类型' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.placeholders.grantTypes' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.validation.appNameRequired', 'zh-CN', 'sys', '应用名称不能为空' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.validation.appNameRequired' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.validation.appNameMaxLength', 'zh-CN', 'sys', '应用名称长度不能超过100' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.validation.appNameMaxLength' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.validation.atLeastOneRedirectUri', 'zh-CN', 'sys', '至少需要一个重定向URI' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.validation.atLeastOneRedirectUri' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.validation.atLeastOneScope', 'zh-CN', 'sys', '请至少选择一个权限范围' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.validation.atLeastOneScope' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.validation.atLeastOneGrantType', 'zh-CN', 'sys', '请至少选择一个授权类型' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.validation.atLeastOneGrantType' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.addApp', 'zh-CN', 'sys', '新增应用' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.addApp' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.editApp', 'zh-CN', 'sys', '编辑应用' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.editApp' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.search', 'zh-CN', 'sys', '查询' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.search' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.addRedirectUri', 'zh-CN', 'sys', '添加回调地址' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.addRedirectUri' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.addPostLogoutUri', 'zh-CN', 'sys', '添加登出回调' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.addPostLogoutUri' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.save', 'zh-CN', 'sys', '保存' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.save' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.cancel', 'zh-CN', 'sys', '取消' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.cancel' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.edit', 'zh-CN', 'sys', '编辑' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.edit' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.resetSecret', 'zh-CN', 'sys', '重置密钥' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.resetSecret' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.delete', 'zh-CN', 'sys', '删除' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.delete' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.copy', 'zh-CN', 'sys', '复制' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.copy' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.saved', 'zh-CN', 'sys', '我已保存' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.saved' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.secretModal.title', 'zh-CN', 'sys', 'Client Secret（仅显示一次）' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.secretModal.title' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.secretModal.warning', 'zh-CN', 'sys', '此密钥仅显示一次，关闭后将无法再次查看。请立即复制并妥善保存。' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.secretModal.warning' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.secretModal.clientIdLabel', 'zh-CN', 'sys', 'Client ID' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.secretModal.clientIdLabel' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.secretModal.clientSecretLabel', 'zh-CN', 'sys', 'Client Secret' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.secretModal.clientSecretLabel' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.toast.queryFailed', 'zh-CN', 'sys', '查询失败' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.toast.queryFailed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.toast.modifySuccess', 'zh-CN', 'sys', '修改成功' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.toast.modifySuccess' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.toast.operationFailed', 'zh-CN', 'sys', '操作失败' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.toast.operationFailed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.toast.enabled', 'zh-CN', 'sys', '已启用' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.toast.enabled' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.toast.disabled', 'zh-CN', 'sys', '已禁用' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.toast.disabled' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.toast.resetSecretFailed', 'zh-CN', 'sys', '重置密钥失败' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.toast.resetSecretFailed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.toast.deleteSuccess', 'zh-CN', 'sys', '删除成功' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.toast.deleteSuccess' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.toast.deleteFailed', 'zh-CN', 'sys', '删除失败' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.toast.deleteFailed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.toast.copied', 'zh-CN', 'sys', '已复制到剪贴板' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.toast.copied' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.toast.copyFailed', 'zh-CN', 'sys', '复制失败，请手动选择复制' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.toast.copyFailed' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.clientId', 'en', 'sys', 'Client ID' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.clientId' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.appName', 'en', 'sys', 'App Name' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.appName' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.grantType', 'en', 'sys', 'Grant Type' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.grantType' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.status', 'en', 'sys', 'Status' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.status' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.createTime', 'en', 'sys', 'Created At' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.createTime' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.operation', 'en', 'sys', 'Operation' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.operation' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.enabled', 'en', 'sys', 'Enabled' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.enabled' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.disabled', 'en', 'sys', 'Disabled' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.disabled' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.grantTypes.authCode', 'en', 'sys', 'authorization_code (Auth Code)' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.grantTypes.authCode' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.grantTypes.refreshToken', 'en', 'sys', 'refresh_token (Refresh Token)' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.grantTypes.refreshToken' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.grantTypes.clientCredentials', 'en', 'sys', 'client_credentials (Client Credentials)' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.grantTypes.clientCredentials' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.placeholders.search', 'en', 'sys', 'Search Client ID / App Name' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.placeholders.search' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.placeholders.clientName', 'en', 'sys', 'e.g., Mobile App' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.placeholders.clientName' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.placeholders.redirectUri', 'en', 'sys', 'https://example.com/callback' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.placeholders.redirectUri' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.placeholders.postLogoutRedirectUri', 'en', 'sys', 'https://example.com/post-logout' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.placeholders.postLogoutRedirectUri' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.placeholders.scopes', 'en', 'sys', 'Select or input scope' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.placeholders.scopes' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.placeholders.grantTypes', 'en', 'sys', 'Select grant types' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.placeholders.grantTypes' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.validation.appNameRequired', 'en', 'sys', 'App name is required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.validation.appNameRequired' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.validation.appNameMaxLength', 'en', 'sys', 'App name cannot exceed 100 characters' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.validation.appNameMaxLength' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.validation.atLeastOneRedirectUri', 'en', 'sys', 'At least one redirect URI is required' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.validation.atLeastOneRedirectUri' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.validation.atLeastOneScope', 'en', 'sys', 'Please select at least one scope' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.validation.atLeastOneScope' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.validation.atLeastOneGrantType', 'en', 'sys', 'Please select at least one grant type' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.validation.atLeastOneGrantType' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.addApp', 'en', 'sys', 'Add App' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.addApp' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.editApp', 'en', 'sys', 'Edit App' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.editApp' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.search', 'en', 'sys', 'Search' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.search' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.addRedirectUri', 'en', 'sys', 'Add Redirect URI' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.addRedirectUri' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.addPostLogoutUri', 'en', 'sys', 'Add Post-Logout URI' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.addPostLogoutUri' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.save', 'en', 'sys', 'Save' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.save' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.cancel', 'en', 'sys', 'Cancel' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.cancel' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.edit', 'en', 'sys', 'Edit' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.edit' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.resetSecret', 'en', 'sys', 'Reset Secret' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.resetSecret' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.delete', 'en', 'sys', 'Delete' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.delete' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.copy', 'en', 'sys', 'Copy' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.copy' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.buttons.saved', 'en', 'sys', 'I have saved' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.buttons.saved' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.secretModal.title', 'en', 'sys', 'Client Secret (shown only once)' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.secretModal.title' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.secretModal.warning', 'en', 'sys', 'This secret is shown only once and cannot be viewed again. Please copy and save it immediately.' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.secretModal.warning' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.secretModal.clientIdLabel', 'en', 'sys', 'Client ID' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.secretModal.clientIdLabel' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.secretModal.clientSecretLabel', 'en', 'sys', 'Client Secret' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.secretModal.clientSecretLabel' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.toast.queryFailed', 'en', 'sys', 'Failed to query' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.toast.queryFailed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.toast.modifySuccess', 'en', 'sys', 'Modified successfully' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.toast.modifySuccess' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.toast.operationFailed', 'en', 'sys', 'Operation failed' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.toast.operationFailed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.toast.enabled', 'en', 'sys', 'Enabled' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.toast.enabled' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.toast.disabled', 'en', 'sys', 'Disabled' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.toast.disabled' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.toast.resetSecretFailed', 'en', 'sys', 'Failed to reset secret' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.toast.resetSecretFailed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.toast.deleteSuccess', 'en', 'sys', 'Deleted successfully' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.toast.deleteSuccess' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.toast.deleteFailed', 'en', 'sys', 'Delete failed' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.toast.deleteFailed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.toast.copied', 'en', 'sys', 'Copied to clipboard' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.toast.copied' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.app.toast.copyFailed', 'en', 'sys', 'Copy failed, please copy manually' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.app.toast.copyFailed' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.message.not.found', 'zh-CN', 'i18n', '翻译不存在: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.message.not.found' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.message.not.found', 'en', 'i18n', 'Translation not found: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.message.not.found' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.import.unknown.keys', 'zh-CN', 'i18n', '导入失败，包含未知 key: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.import.unknown.keys' AND m.locale = 'zh-CN');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'i18n.import.unknown.keys', 'en', 'i18n', 'Import failed, contains unknown keys: {0}' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'i18n.import.unknown.keys' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.1.name', 'en', 'sys', 'System' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.1.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.2.name', 'en', 'sys', 'Users' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.2.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.3.name', 'en', 'sys', 'Add User' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.3.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.4.name', 'en', 'sys', 'Edit User' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.4.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.5.name', 'en', 'sys', 'Delete User' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.5.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.6.name', 'en', 'sys', 'Reset Password' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.6.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.7.name', 'en', 'sys', 'Assign Roles' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.7.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.8.name', 'en', 'sys', 'Unlock User' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.8.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.10.name', 'en', 'sys', 'Roles' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.10.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.11.name', 'en', 'sys', 'Add Role' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.11.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.12.name', 'en', 'sys', 'Edit Role' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.12.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.13.name', 'en', 'sys', 'Delete Role' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.13.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.14.name', 'en', 'sys', 'Permissions' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.14.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.20.name', 'en', 'sys', 'Menus' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.20.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.21.name', 'en', 'sys', 'Add Menu' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.21.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.22.name', 'en', 'sys', 'Edit Menu' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.22.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.23.name', 'en', 'sys', 'Delete Menu' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.23.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.30.name', 'en', 'sys', 'Units' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.30.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.31.name', 'en', 'sys', 'Add Unit' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.31.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.32.name', 'en', 'sys', 'Edit Unit' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.32.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.33.name', 'en', 'sys', 'Delete Unit' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.33.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.40.name', 'en', 'sys', 'Config' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.40.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.41.name', 'en', 'sys', 'Add Config' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.41.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.42.name', 'en', 'sys', 'Edit Config' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.42.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.50.name', 'en', 'sys', 'Sessions' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.50.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.51.name', 'en', 'sys', 'Audit Log' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.51.name' AND m.locale = 'en');
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.55.name', 'en', 'sys', 'Publish' WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.55.name' AND m.locale = 'en');

-- zh-CN menu names: seeded from sys_menu.menu_name at runtime
INSERT INTO i18n_message (message_key, locale, module, value) SELECT 'sys.menu.' || sm.id || '.name', 'zh-CN', 'sys', sm.menu_name FROM sys_menu sm WHERE NOT EXISTS (SELECT 1 FROM i18n_message m WHERE m.message_key = 'sys.menu.' || sm.id || '.name' AND m.locale = 'zh-CN');

-- ===== sys 基础种子数据（admin 用户/角色/单位/配置 + 核心菜单）=====

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
    (8,  2,    '解锁用户', 'BUTTON',    NULL,        NULL,                   'sys:user:unlock', NULL,              6, 1, 1),
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

-- ===== AI 配置种子 =====

-- AI Copilot 配置种子：把 DeepSeek 连接参数迁到 sys_config（界面可配），不再依赖服务器环境变量。
-- 去重插入，兼容已存在数据 / 重复执行。
INSERT INTO sys_config (config_key, config_value, config_type, description, category)
SELECT 'ai.deepseek.api-key', '', 'SECRET',
   'DeepSeek/OpenAI 兼容 API Key（界面配置，勿放入服务器环境变量）', 'ai'
WHERE NOT EXISTS (SELECT 1 FROM sys_config c WHERE c.config_key = 'ai.deepseek.api-key');
INSERT INTO sys_config (config_key, config_value, config_type, description, category)
SELECT 'ai.deepseek.base-url', 'https://api.deepseek.com', 'STRING',
   'OpenAI 兼容 base-url，默认指向 DeepSeek 官方', 'ai'
WHERE NOT EXISTS (SELECT 1 FROM sys_config c WHERE c.config_key = 'ai.deepseek.base-url');
INSERT INTO sys_config (config_key, config_value, config_type, description, category)
SELECT 'ai.deepseek.model', 'deepseek-chat', 'STRING',
   '模型名，默认 deepseek-chat', 'ai'
WHERE NOT EXISTS (SELECT 1 FROM sys_config c WHERE c.config_key = 'ai.deepseek.model');

-- ===== 菜单名翻译（i18n_message）=====

-- =============================================
-- i18n 管理菜单（已迁移至代码注册）+ 菜单名翻译数据
-- =============================================
-- 此前的 sys_menu + sys_role_menu INSERT 已迁移到 I18nMenuConfiguration（MenuContributor），
-- 由 sys 模块的 MenuBootstrap 启动时幂等自动注册 + 绑定 admin 角色。
-- 此文件保留 i18n_message（菜单名翻译）数据播种。

-- 国际化管理菜单自身的显示名 i18n key（与 MenuContributor 注册的 permission 对应的菜单显示名翻译）
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

-- openapp 菜单英文名

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

-- ===== 同步 IDENTITY 序列（种子用显式 id 插入后推进序列）=====
-- PG 专有：种子用显式 id 插入后推进 BIGSERIAL 序列，避免后续 JPA 插入 nextval 返回旧值导致主键冲突。
-- H2 的 GENERATED BY DEFAULT AS IDENTITY 自动推进，无需 setval；
-- PG 的 setval 已移至 db/migration/postgresql/V99__sync_sequences.sql（按 Flyway {vendor} 目录仅 PG 执行）。
