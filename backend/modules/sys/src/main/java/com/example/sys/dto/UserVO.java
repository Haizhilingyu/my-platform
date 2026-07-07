package com.example.sys.dto;

import com.example.sys.domain.SysUser;
import java.time.LocalDateTime;
import lombok.Data;

/** 用户视图对象。 */
@Data
public class UserVO {

  private Long id;
  private String username;
  private String realName;
  private String email;
  private String phone;
  private Long unitId;
  private String unitName;
  private String avatar;
  private Integer status;
  private Boolean locked;
  private String remark;
  private LocalDateTime createdAt;

  public static UserVO of(SysUser user) {
    UserVO vo = new UserVO();
    vo.setId(user.getId());
    vo.setUsername(user.getUsername());
    vo.setRealName(user.getRealName());
    vo.setEmail(user.getEmail());
    vo.setPhone(user.getPhone());
    vo.setUnitId(user.getUnitId());
    vo.setAvatar(user.getAvatar());
    vo.setStatus(user.getStatus());
    vo.setRemark(user.getRemark());
    vo.setCreatedAt(user.getCreatedAt());
    return vo;
  }
}
