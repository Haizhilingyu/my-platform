package com.example.notify.enums;

/** 消息紧急度。决定投递策略：URGENT 立即 WebSocket 推送；其他仅入收件箱。 */
public enum MessageLevel {
  URGENT,
  IMPORTANT,
  NORMAL
}
