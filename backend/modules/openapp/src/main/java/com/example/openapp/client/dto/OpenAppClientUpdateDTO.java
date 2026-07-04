package com.example.openapp.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 更新外部应用客户端请求。不能修改 clientId / clientSecret（secret 通过 reset-secret 单独管理）。 */
public record OpenAppClientUpdateDTO(
    @NotBlank(message = "应用名称不能为空") String clientName,
    List<String> redirectUris,
    List<String> postLogoutRedirectUris,
    List<String> scopes,
    List<String> grantTypes,
    @NotNull(message = "enabled 不能为空") Boolean enabled) {}
