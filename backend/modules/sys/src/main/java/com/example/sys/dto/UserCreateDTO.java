package com.example.sys.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** 用户创建 DTO。 */
@Data
public class UserCreateDTO {

  @NotBlank(message = "用户名不能为空")
  @Size(min = 3, max = 32, message = "用户名长度需在3-32之间")
  @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字、下划线")
  private String username;

  @NotBlank(message = "密码不能为空")
  @Size(min = 6, max = 32, message = "密码长度需在6-32之间")
  private String password;

  @Size(max = 50, message = "姓名长度不能超过50")
  private String realName;

  @Email(message = "邮箱格式不正确")
  @Size(max = 100, message = "邮箱长度不能超过100")
  private String email;

  @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
  @Size(max = 20, message = "手机号长度不能超过20")
  private String phone;

  private Long unitId;

  @Size(max = 500, message = "头像URL长度不能超过500")
  private String avatar;

  private List<Long> roleIds;
}
