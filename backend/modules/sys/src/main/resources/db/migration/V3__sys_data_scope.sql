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
