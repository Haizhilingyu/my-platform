package com.example.openapp.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 创建外部应用客户端请求。clientId 与 clientSecret 由服务端生成并在响应中返回（secret 仅返回一次）。 */
public record OpenAppClientCreateDTO(
    @NotBlank(message = "应用名称不能为空") @Size(max = 100, message = "应用名称长度不能超过100") String clientName,
    @NotEmpty(message = "redirectUris 不能为空") List<String> redirectUris,
    List<String> postLogoutRedirectUris,
    @NotEmpty(message = "scopes 不能为空") List<String> scopes,
    @NotEmpty(message = "grantTypes 不能为空") List<String> grantTypes) {}
