package com.example.openapp.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 创建外部应用客户端请求。clientId 与 clientSecret 由服务端生成并在响应中返回（secret 仅返回一次）。 */
public record OpenAppClientCreateDTO(
    @NotBlank(message = "{validation.app.clientName.notBlank}")
        @Size(max = 100, message = "{validation.app.clientName.size}")
        String clientName,
    @NotEmpty(message = "{validation.app.redirectUris.notEmpty}") List<String> redirectUris,
    List<String> postLogoutRedirectUris,
    @NotEmpty(message = "{validation.app.scopes.notEmpty}") List<String> scopes,
    @NotEmpty(message = "{validation.app.grantTypes.notEmpty}") List<String> grantTypes) {}
