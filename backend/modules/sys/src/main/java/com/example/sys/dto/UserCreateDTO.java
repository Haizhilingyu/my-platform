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

  @NotBlank(message = "{validation.user.username.notBlank}")
  @Size(min = 3, max = 32, message = "{validation.user.username.size}")
  @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "{validation.user.username.pattern}")
  private String username;

  @NotBlank(message = "{validation.user.password.notBlank}")
  @Size(min = 6, max = 32, message = "{validation.user.password.size}")
  private String password;

  @Size(max = 50, message = "{validation.user.realName.size}")
  private String realName;

  @Email(message = "{validation.user.email.pattern}")
  @Size(max = 100, message = "{validation.user.email.size}")
  private String email;

  @Pattern(regexp = "^1[3-9]\\d{9}$", message = "{validation.user.phone.pattern}")
  @Size(max = 20, message = "{validation.user.phone.size}")
  private String phone;

  private Long unitId;

  @Size(max = 500, message = "{validation.user.avatar.size}")
  private String avatar;

  private List<Long> roleIds;
}
