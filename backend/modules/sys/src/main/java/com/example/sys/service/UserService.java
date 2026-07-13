package com.example.sys.service;

import com.example.common.datapolicy.DataScopeSpecification;
import com.example.common.exception.BizException;
import com.example.common.exception.NotFoundException;
import com.example.common.i18n.Messages;
import com.example.common.security.CurrentUser;
import com.example.sys.domain.SysUnit;
import com.example.sys.domain.SysUser;
import com.example.sys.domain.SysUserRole;
import com.example.sys.dto.LoginDTO;
import com.example.sys.dto.LoginVO;
import com.example.sys.dto.UserCreateDTO;
import com.example.sys.dto.UserUpdateDTO;
import com.example.sys.dto.UserVO;
import com.example.sys.events.UserCreated;
import com.example.sys.repository.SysRoleRepository;
import com.example.sys.repository.SysUnitRepository;
import com.example.sys.repository.SysUserRepository;
import com.example.sys.repository.SysUserRoleRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户服务。 */
@Service
@RequiredArgsConstructor
public class UserService {

  private final SysUserRepository userRepository;
  private final SysUserRoleRepository userRoleRepository;
  private final SysRoleRepository roleRepository;
  private final SysUnitRepository unitRepository;
  private final PermissionService permissionService;
  private final PasswordEncoder passwordEncoder;
  private final ApplicationEventPublisher eventPublisher;
  private final DataScopeResolver dataScopeResolver;
  private final LoginSecurityService loginSecurityService;

  @Transactional(readOnly = true)
  public Page<UserVO> search(String keyword, Long unitId, Integer status, Pageable pageable) {
    Specification<SysUser> spec = buildSearchSpec(keyword, unitId, status);

    CurrentUser.UserInfo current = CurrentUser.get();
    if (current != null) {
      DataScopeResolver.ResolvedScope scope =
          dataScopeResolver.resolve(current.userId(), current.unitId());
      if (scope != null) {
        spec = spec.and(DataScopeSpecification.of(scope.toScopeData(), scope.descendantUnitIds()));
      }
    }

    Page<SysUser> page = userRepository.findAll(spec, pageable);
    return page.map(
        u -> {
          UserVO vo = toVO(u);
          vo.setLocked(loginSecurityService.isLocked(u.getUsername()));
          return vo;
        });
  }

  private Specification<SysUser> buildSearchSpec(String keyword, Long unitId, Integer status) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (keyword != null && !keyword.isBlank()) {
        String pattern = "%" + keyword.toLowerCase() + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("username")), pattern),
                cb.like(cb.lower(root.get("realName")), pattern),
                cb.like(cb.lower(root.get("phone")), pattern)));
      }
      if (unitId != null) {
        predicates.add(cb.equal(root.get("unitId"), unitId));
      }
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  @Transactional(readOnly = true)
  public UserVO getById(Long id) {
    return toVO(
        userRepository
            .findById(id)
            .orElseThrow(
                () ->
                    NotFoundException.i18n(
                        "error.resource.not.found", Messages.get("resource.user"), id)));
  }

  @Transactional
  public Long create(UserCreateDTO dto) {
    if (userRepository.existsByUsername(dto.getUsername())) {
      throw BizException.i18n("user.username.exists", dto.getUsername());
    }

    SysUser user =
        SysUser.builder()
            .username(dto.getUsername())
            .password(passwordEncoder.encode(dto.getPassword()))
            .realName(dto.getRealName())
            .email(dto.getEmail())
            .phone(dto.getPhone())
            .avatar(dto.getAvatar())
            .status(1)
            .build();
    user.setUnitId(dto.getUnitId());

    user = userRepository.save(user);

    if (dto.getRoleIds() != null && !dto.getRoleIds().isEmpty()) {
      permissionService.assignRoles(user.getId(), dto.getRoleIds());
    }

    eventPublisher.publishEvent(new UserCreated(user.getId(), user.getUsername()));
    return user.getId();
  }

  @Transactional
  public void update(Long id, UserUpdateDTO dto) {
    SysUser user =
        userRepository
            .findById(id)
            .orElseThrow(
                () ->
                    NotFoundException.i18n(
                        "error.resource.not.found", Messages.get("resource.user"), id));

    if (dto.getRealName() != null) {
      user.setRealName(dto.getRealName());
    }
    if (dto.getEmail() != null) {
      user.setEmail(dto.getEmail());
    }
    if (dto.getPhone() != null) {
      user.setPhone(dto.getPhone());
    }
    if (dto.getUnitId() != null) {
      user.setUnitId(dto.getUnitId());
    }
    if (dto.getAvatar() != null) {
      user.setAvatar(dto.getAvatar());
    }
    if (dto.getStatus() != null) {
      user.setStatus(dto.getStatus());
    }
    if (dto.getRemark() != null) {
      user.setRemark(dto.getRemark());
    }
    userRepository.save(user);
  }

  @Transactional
  public void delete(Long id) {
    SysUser user =
        userRepository
            .findById(id)
            .orElseThrow(
                () ->
                    NotFoundException.i18n(
                        "error.resource.not.found", Messages.get("resource.user"), id));
    userRoleRepository.deleteByUserId(id);
    userRepository.delete(user);
  }

  /**
   * 批量删除用户，带数据权限范围保护。
   *
   * <p>若目标 ID 中存在当前用户不可见的用户（超出其数据权限范围），抛出 403 拒绝整批操作。
   *
   * @param ids 待删除的用户 ID 列表
   */
  @Transactional
  public void deleteBatch(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return;
    }

    CurrentUser.UserInfo current = CurrentUser.get();
    if (current != null) {
      DataScopeResolver.ResolvedScope scope =
          dataScopeResolver.resolve(current.userId(), current.unitId());
      if (scope != null) {
        Specification<SysUser> scopeSpec =
            DataScopeSpecification.of(scope.toScopeData(), scope.descendantUnitIds());
        Specification<SysUser> idSpec = (root, query, cb) -> root.get("id").in(ids);
        long visibleCount = userRepository.count(scopeSpec.and(idSpec));
        if (visibleCount != ids.size()) {
          throw BizException.i18n(403, "user.delete.forbidden");
        }
      }
    }

    for (Long id : ids) {
      userRoleRepository.deleteByUserId(id);
    }
    userRepository.deleteAllByIdInBatch(ids);
  }

  @Transactional
  public void assignRoles(Long userId, java.util.List<Long> roleIds) {
    userRepository
        .findById(userId)
        .orElseThrow(
            () ->
                NotFoundException.i18n(
                    "error.resource.not.found", Messages.get("resource.user"), userId));
    permissionService.assignRoles(userId, roleIds);
  }

  @Transactional(readOnly = true)
  public java.util.List<Long> getUserRoleIds(Long userId) {
    return userRoleRepository.findByUserId(userId).stream().map(SysUserRole::getRoleId).toList();
  }

  @Transactional
  public void resetPassword(Long id, String newPassword) {
    SysUser user =
        userRepository
            .findById(id)
            .orElseThrow(
                () ->
                    NotFoundException.i18n(
                        "error.resource.not.found", Messages.get("resource.user"), id));
    user.setPassword(passwordEncoder.encode(newPassword));
    userRepository.save(user);
    loginSecurityService.unlock(user.getUsername());
  }

  /** 更新用户语言偏好，返回更新后的实体（供调用方读取新 locale 构建 JWT）。 */
  @Transactional
  public SysUser updateLocale(Long userId, String locale) {
    SysUser user = getEntityById(userId);
    user.setLocale(locale);
    return userRepository.save(user);
  }

  @Transactional(readOnly = true)
  public LoginVO login(LoginDTO dto) {
    SysUser user =
        userRepository
            .findByUsername(dto.getUsername())
            .orElseThrow(() -> BizException.i18n(401, "error.auth.bad.credentials"));
    if (user.getStatus() != 1) {
      throw BizException.i18n(403, "error.user.disabled");
    }
    if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
      throw BizException.i18n(401, "error.auth.bad.credentials");
    }
    // Token 生成由 AuthController 处理（需要 JwtUtil）
    UserVO vo = toVO(user);
    return new LoginVO(null, "Bearer", vo);
  }

  @Transactional(readOnly = true)
  public SysUser getEntityById(Long id) {
    return userRepository
        .findById(id)
        .orElseThrow(
            () ->
                NotFoundException.i18n(
                    "error.resource.not.found", Messages.get("resource.user"), id));
  }

  @Transactional(readOnly = true)
  public SysUser getEntityByUsername(String username) {
    return userRepository
        .findByUsername(username)
        .orElseThrow(() -> BizException.i18n(401, "user.not.found.by.username", username));
  }

  private UserVO toVO(SysUser user) {
    UserVO vo = UserVO.of(user);
    if (user.getUnitId() != null) {
      Optional<SysUnit> unit = unitRepository.findById(user.getUnitId());
      unit.ifPresent(u -> vo.setUnitName(u.getUnitName()));
    }
    return vo;
  }
}
