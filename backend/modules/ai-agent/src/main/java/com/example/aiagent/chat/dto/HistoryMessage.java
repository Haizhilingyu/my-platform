package com.example.aiagent.chat.dto;

/** 历史消息（role: user/assistant；id 为数据库主键，供单条删除定位）。 */
public record HistoryMessage(Long id, String role, String text) {}
