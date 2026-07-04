package com.example.sys.dto;

import lombok.Data;

/** 用户更新 DTO。 */
@Data
public class UserUpdateDTO {

  private String realName;
  private String email;
  private String phone;
  private Long unitId;
  private String avatar;
  private Integer status;
  private String remark;
}
