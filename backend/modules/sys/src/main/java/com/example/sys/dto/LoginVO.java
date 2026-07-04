package com.example.sys.dto;

import com.example.common.login.LoginResult;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 登录响应 DTO。 */
@Data
@AllArgsConstructor
public class LoginVO implements LoginResult {
    private String token;
    private String tokenType;
    private UserVO user;
}
