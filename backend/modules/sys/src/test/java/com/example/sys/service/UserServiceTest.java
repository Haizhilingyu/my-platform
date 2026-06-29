package com.example.sys.service;

import com.example.common.exception.BizException;
import com.example.common.exception.NotFoundException;
import com.example.sys.domain.SysRole;
import com.example.sys.domain.SysUser;
import com.example.sys.domain.SysUserRole;
import com.example.sys.dto.LoginDTO;
import com.example.sys.dto.UserCreateDTO;
import com.example.sys.dto.UserUpdateDTO;
import com.example.sys.dto.UserVO;
import com.example.sys.events.UserCreated;
import com.example.sys.repository.SysUnitRepository;
import com.example.sys.repository.SysUserRoleRepository;
import com.example.sys.repository.SysUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * UserService 单元测试。
 *
 * <p>TDD 示范：遵循 Given / When / Then 结构，方法名用 should...when... 句式。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户服务")
class UserServiceTest {

    @Mock private SysUserRepository userRepository;
    @Mock private SysUserRoleRepository userRoleRepository;
    @Mock private SysUnitRepository unitRepository;
    @Mock private PermissionService permissionService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private UserService userService;

    // ==================== 创建用户 ====================

    @Nested
    @DisplayName("创建用户")
    class CreateUser {

        @Test
        @DisplayName("正常创建：密码加密、角色分配、事件发布")
        void should_encryptPassword_and_publishEvent_when_createUser() {
            // Given
            var dto = new UserCreateDTO();
            dto.setUsername("newuser");
            dto.setPassword("plain123");
            dto.setRealName("测试用户");
            dto.setRoleIds(List.of(1L, 2L));

            var savedUser = SysUser.builder().id(100L).username("newuser").build();
            when(userRepository.existsByUsername("newuser")).thenReturn(false);
            when(passwordEncoder.encode("plain123")).thenReturn("$2a$10$encoded");
            when(userRepository.save(any(SysUser.class))).thenReturn(savedUser);

            // When
            Long userId = userService.create(dto);

            // Then — 验证返回值
            assertThat(userId).isEqualTo(100L);

            // Then — 验证密码被加密
            ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getPassword()).isEqualTo("$2a$10$encoded");

            // Then — 验证角色分配
            verify(permissionService).assignRoles(100L, List.of(1L, 2L));

            // Then — 验证事件发布
            verify(eventPublisher).publishEvent(any(UserCreated.class));
        }

        @Test
        @DisplayName("用户名重复：抛出 BizException")
        void should_throwBizException_when_usernameExists() {
            // Given
            var dto = new UserCreateDTO();
            dto.setUsername("admin");
            dto.setPassword("123");
            when(userRepository.existsByUsername("admin")).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> userService.create(dto))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("用户名已存在");
            verify(userRepository, never()).save(any());
        }
    }

    // ==================== 查询用户 ====================

    @Nested
    @DisplayName("查询用户")
    class GetUser {

        @Test
        @DisplayName("ID 存在：返回 UserVO")
        void should_returnUserVO_when_idExists() {
            // Given
            var user = SysUser.builder()
                    .id(1L).username("admin").realName("管理员")
                    .unitId(10L).status(1).build();
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            // When
            UserVO vo = userService.getById(1L);

            // Then
            assertThat(vo.getUsername()).isEqualTo("admin");
            assertThat(vo.getRealName()).isEqualTo("管理员");
        }

        @Test
        @DisplayName("ID 不存在：抛出 NotFoundException")
        void should_throwNotFound_when_idNotExists() {
            // Given
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.getById(999L))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    // ==================== 更新用户 ====================

    @Nested
    @DisplayName("更新用户")
    class UpdateUser {

        @Test
        @DisplayName("部分更新：只更新非 null 字段")
        void should_updateOnlyNonNullFields_when_partialUpdate() {
            // Given
            var existing = SysUser.builder()
                    .id(1L).username("admin").realName("旧名称")
                    .email("old@test.com").status(1).build();
            when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

            var dto = new UserUpdateDTO();
            dto.setRealName("新名称");
            // email, status 不传（null），不应被覆盖

            // When
            userService.update(1L, dto);

            // Then
            ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getRealName()).isEqualTo("新名称");
            assertThat(captor.getValue().getEmail()).isEqualTo("old@test.com");
            assertThat(captor.getValue().getStatus()).isEqualTo(1);
        }
    }

    // ==================== 删除用户 ====================

    @Nested
    @DisplayName("删除用户")
    class DeleteUser {

        @Test
        @DisplayName("正常删除：清除角色关联 + 删除用户")
        void should_clearRolesAndDelete_when_deleteUser() {
            // Given
            var user = SysUser.builder().id(1L).build();
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            // When
            userService.delete(1L);

            // Then
            verify(userRoleRepository).deleteByUserId(1L);
            verify(userRepository).delete(user);
        }
    }

    // ==================== 分配角色 ====================

    @Test
    @DisplayName("分配角色：先清后建")
    void should_clearAndReassign_when_assignRoles() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(SysUser.builder().id(1L).build()));

        // When
        userService.assignRoles(1L, List.of(3L, 5L));

        // Then
        verify(permissionService).assignRoles(1L, List.of(3L, 5L));
    }
}
