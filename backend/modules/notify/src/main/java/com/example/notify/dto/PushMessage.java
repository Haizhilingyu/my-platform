package com.example.notify.dto;

import com.example.notify.enums.MessageLevel;
import java.util.Map;

/**
 * 推送给 WebSocket 客户端的消息载荷。
 *
 * <p>序列化后形如：
 *
 * <pre>{@code
 * { "type": "message", "level": "URGENT", "messageId": 100, "title": "...", "content": "..." }
 * }</pre>
 */
public record PushMessage(
    String type, MessageLevel level, Long messageId, Long seq, String title, String content) {

  public static final String TYPE_MESSAGE = "message";

  public static PushMessage of(
      Long messageId, Long seq, MessageLevel level, String title, String content) {
    return new PushMessage(TYPE_MESSAGE, level, messageId, seq, title, content);
  }

  public Map<String, Object> asMap() {
    return Map.of(
        "type", type,
        "level", level.name(),
        "messageId", messageId,
        "seq", seq,
        "title", title,
        "content", content);
  }
}
