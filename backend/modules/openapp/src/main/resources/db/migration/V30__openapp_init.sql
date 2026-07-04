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
