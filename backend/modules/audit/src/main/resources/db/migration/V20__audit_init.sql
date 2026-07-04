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
