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
