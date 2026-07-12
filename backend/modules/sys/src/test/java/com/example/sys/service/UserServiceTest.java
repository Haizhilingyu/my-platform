package com.example.sys.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.example.common.datapolicy.DataScope;
import com.example.common.exception.BizException;
import com.example.common.exception.NotFoundException;
import com.example.common.i18n.Messages;
import com.example.common.security.CurrentUser;
import com.example.sys.domain.SysUser;
import com.example.sys.dto.UserCreateDTO;
import com.example.sys.dto.UserUpdateDTO;
import com.example.sys.dto.UserVO;
import com.example.sys.events.UserCreated;
import com.example.sys.repository.SysUnitRepository;
import com.example.sys.repository.SysUserRepository;
import com.example.sys.repository.SysUserRoleRepository;
import com.example.sys.service.DataScopeResolver.ResolvedScope;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

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
  @Mock private DataScopeResolver dataScopeResolver;

  @InjectMocks private UserService userService;

  @BeforeEach
  void initMessages() {
    ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
    ms.setBasename("classpath:i18n/messages");
    ms.setDefaultEncoding("UTF-8");
    ms.setFallbackToSystemLocale(false);
    ms.setUseCodeAsDefaultMessage(true);
    ReflectionTestUtils.invokeMethod(new Messages(ms), "init");
  }

  @AfterEach
  void clearCurrentUser() {
    CurrentUser.clear();
  }

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
      var user = SysUser.builder().id(1L).username("admin").realName("管理员").status(1).build();
      user.setUnitId(10L);
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
      assertThatThrownBy(() -> userService.getById(999L)).isInstanceOf(NotFoundException.class);
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
      var existing =
          SysUser.builder()
              .id(1L)
              .username("admin")
              .realName("旧名称")
              .email("old@test.com")
              .status(1)
              .build();
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

  // ==================== 批量删除（数据权限保护） ====================

  @Nested
  @DisplayName("批量删除")
  class DeleteBatch {

    @Test
    @DisplayName("空列表：不执行任何操作")
    void should_doNothing_when_emptyIds() {
      userService.deleteBatch(Collections.emptyList());
      verifyNoInteractions(userRepository, userRoleRepository, dataScopeResolver);
    }

    @Test
    @DisplayName("ALL 范围：跳过范围校验，直接删除")
    void should_skipScopeCheck_when_allScope() {
      // Given
      CurrentUser.set(new CurrentUser.UserInfo(1L, "admin", 1L, Set.of("admin"), Set.of()));
      when(dataScopeResolver.resolve(1L, 1L)).thenReturn(null);
      List<Long> ids = List.of(10L, 20L);

      // When
      userService.deleteBatch(ids);

      // Then
      verify(userRoleRepository).deleteByUserId(10L);
      verify(userRoleRepository).deleteByUserId(20L);
      verify(userRepository).deleteAllByIdInBatch(ids);
      verify(userRepository, never()).count(any(Specification.class));
    }

    @Test
    @DisplayName("UNIT 范围、目标全可见：删除成功")
    void should_delete_when_allIdsWithinScope() {
      // Given
      CurrentUser.set(new CurrentUser.UserInfo(2L, "manager", 2L, Set.of(), Set.of()));
      ResolvedScope scope = new ResolvedScope(DataScope.UNIT, 2L, Set.of(), Set.of(), 2L);
      when(dataScopeResolver.resolve(2L, 2L)).thenReturn(scope);
      List<Long> ids = List.of(10L, 20L);
      when(userRepository.count(any(Specification.class))).thenReturn((long) ids.size());

      // When
      userService.deleteBatch(ids);

      // Then
      verify(userRepository).deleteAllByIdInBatch(ids);
    }

    @Test
    @DisplayName("UNIT 范围、存在不可见目标：抛出 403 BizException")
    void should_throw403_when_someIdsOutOfScope() {
      // Given
      CurrentUser.set(new CurrentUser.UserInfo(2L, "manager", 2L, Set.of(), Set.of()));
      ResolvedScope scope = new ResolvedScope(DataScope.UNIT, 2L, Set.of(), Set.of(), 2L);
      when(dataScopeResolver.resolve(2L, 2L)).thenReturn(scope);
      List<Long> ids = List.of(10L, 20L, 99L);
      when(userRepository.count(any(Specification.class))).thenReturn(2L);

      // When & Then
      assertThatThrownBy(() -> userService.deleteBatch(ids))
          .isInstanceOf(BizException.class)
          .hasMessageContaining("无权删除");
      verify(userRepository, never()).deleteAllByIdInBatch(any());
    }
  }

  // ==================== 查询（数据权限过滤） ====================

  @Nested
  @DisplayName("搜索（数据权限）")
  class SearchWithScope {

    @Test
    @DisplayName("无 CurrentUser：不加范围过滤，直接查询")
    void should_notApplyScope_when_noCurrentUser() {
      // Given
      Page<SysUser> page = new PageImpl<>(List.of());
      when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

      // When
      userService.search(null, null, null, PageRequest.of(0, 10));

      // Then
      verify(dataScopeResolver, never()).resolve(anyLong(), any());
    }

    @Test
    @DisplayName("ALL 范围：resolve 返回 null，不加范围过滤")
    void should_notApplyScope_when_allScope() {
      // Given
      CurrentUser.set(new CurrentUser.UserInfo(1L, "admin", 1L, Set.of("admin"), Set.of()));
      when(dataScopeResolver.resolve(1L, 1L)).thenReturn(null);
      Page<SysUser> page = new PageImpl<>(List.of());
      when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

      // When
      userService.search(null, null, null, PageRequest.of(0, 10));

      // Then — resolve 被调用，但结果为 null（ALL），不附加范围
      verify(dataScopeResolver).resolve(1L, 1L);
    }
  }
}
