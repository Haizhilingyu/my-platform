package com.example.openapp.client.dto;

import java.time.Instant;
import java.util.List;

/** 外部应用客户端视图（响应）。永不返回 client_secret。 */
public record OpenAppClientVO(
    Long id,
    String clientId,
    String clientName,
    List<String> redirectUris,
    List<String> postLogoutRedirectUris,
    List<String> scopes,
    List<String> grantTypes,
    boolean enabled,
    Instant createdAt) {}
