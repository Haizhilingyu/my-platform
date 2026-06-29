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
