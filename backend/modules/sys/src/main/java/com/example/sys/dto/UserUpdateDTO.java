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

  @Min(value = 0, message = "状态值非法")
  @Max(value = 1, message = "状态值非法")
  private Integer status;

  @Size(max = 200, message = "备注长度不能超过200")
  private String remark;
}
