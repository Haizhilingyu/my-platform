package com.example.sys.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 用户更新 DTO。 */
@Data
public class UserUpdateDTO {

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

  @Min(value = 0, message = "{validation.user.status.invalid}")
  @Max(value = 1, message = "{validation.user.status.invalid}")
  private Integer status;

  @Size(max = 200, message = "{validation.user.remark.size}")
  private String remark;
}
