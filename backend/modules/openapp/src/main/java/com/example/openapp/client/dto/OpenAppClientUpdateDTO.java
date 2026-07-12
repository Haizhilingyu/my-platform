package com.example.openapp.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 更新外部应用客户端请求。不能修改 clientId / clientSecret（secret 通过 reset-secret 单独管理）。 */
public record OpenAppClientUpdateDTO(
    @NotBlank(message = "{validation.app.clientName.notBlank}")
        @Size(max = 100, message = "{validation.app.clientName.size}")
        String clientName,
    List<String> redirectUris,
    List<String> postLogoutRedirectUris,
    List<String> scopes,
    List<String> grantTypes,
    @NotNull(message = "{validation.app.enabled.notNull}") Boolean enabled) {}
