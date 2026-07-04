package com.example.sys.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;

/** 用户创建 DTO。 */
@Data
public class UserCreateDTO {

  @NotBlank(message = "用户名不能为空")
  private String username;

  @NotBlank(message = "密码不能为空")
  private String password;

  private String realName;
  private String email;
  private String phone;
  private Long unitId;
  private String avatar;
  private List<Long> roleIds;
}
