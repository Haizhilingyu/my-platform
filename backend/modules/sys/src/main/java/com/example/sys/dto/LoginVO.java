package com.example.sys.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 登录响应 DTO。 */
@Data
@AllArgsConstructor
public class LoginVO {
    private String token;
    private String tokenType;
    private UserVO user;
}
