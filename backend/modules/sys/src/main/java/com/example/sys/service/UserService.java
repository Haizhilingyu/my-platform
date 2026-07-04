package com.example.sys.service;

import com.example.common.exception.BizException;
import com.example.common.exception.NotFoundException;
import com.example.sys.domain.SysUser;
import com.example.sys.domain.SysUserRole;
import com.example.sys.dto.LoginDTO;
import com.example.sys.dto.LoginVO;
import com.example.sys.dto.UserCreateDTO;
import com.example.sys.dto.UserUpdateDTO;
import com.example.sys.dto.UserVO;
import com.example.sys.events.UserCreated;
import com.example.sys.repository.SysUnitRepository;
import com.example.sys.repository.SysUserRoleRepository;
import com.example.sys.repository.SysUserRepository;
import com.example.sys.repository.SysRoleRepository;
import com.example.sys.domain.SysUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户服务。
 */
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

    @Transactional(readOnly = true)
    public Page<UserVO> search(String keyword, Long unitId, Integer status, Pageable pageable) {
        Page<SysUser> page = userRepository.search(keyword, unitId, status, pageable);
        return page.map(this::toVO);
    }

    @Transactional(readOnly = true)
    public UserVO getById(Long id) {
        return toVO(userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("用户", id)));
    }

    @Transactional
    public Long create(UserCreateDTO dto) {
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new BizException("用户名已存在: " + dto.getUsername());
        }

        SysUser user = SysUser.builder()
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
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("用户", id));

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
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("用户", id));
        userRoleRepository.deleteByUserId(id);
        userRepository.delete(user);
    }

    @Transactional
    public void assignRoles(Long userId, java.util.List<Long> roleIds) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户", userId));
        permissionService.assignRoles(userId, roleIds);
    }

    @Transactional(readOnly = true)
    public java.util.List<Long> getUserRoleIds(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(SysUserRole::getRoleId)
                .toList();
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("用户", id));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public LoginVO login(LoginDTO dto) {
        SysUser user = userRepository.findByUsername(dto.getUsername())
                .orElseThrow(() -> new BizException(401, "用户名或密码错误"));
        if (user.getStatus() != 1) {
            throw new BizException(403, "用户已被禁用");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        // Token 生成由 AuthController 处理（需要 JwtUtil）
        UserVO vo = toVO(user);
        return new LoginVO(null, "Bearer", vo);
    }

    @Transactional(readOnly = true)
    public SysUser getEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("用户", id));
    }

    @Transactional(readOnly = true)
    public SysUser getEntityByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BizException(401, "用户不存在: " + username));
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
