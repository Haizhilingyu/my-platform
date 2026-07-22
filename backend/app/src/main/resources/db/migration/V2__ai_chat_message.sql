-- ===== ai-agent 模块表结构 =====

-- AI 对话消息表：按用户隔离的 Copilot 对话历史（append-only，仅支持单条删除）。

CREATE TABLE IF NOT EXISTS ai_chat_message (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    content     TEXT         NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE ai_chat_message IS 'AI 对话消息表';

CREATE INDEX IF NOT EXISTS idx_ai_chat_message_user_time ON ai_chat_message(user_id, created_at);
