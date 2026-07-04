package com.example.openapp.client.dto;

/** 重置/创建 secret 的响应。明文 secret 仅此一次返回，DB 仅存 BCrypt 哈希。 */
public record OpenAppSecretResult(Long id, String clientId, String clientSecret) {}
