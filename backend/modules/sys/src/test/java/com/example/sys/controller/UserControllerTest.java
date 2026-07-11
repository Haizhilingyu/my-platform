package com.example.sys.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.result.PageResult;
import com.example.common.result.Result;
import com.example.sys.domain.SysUser;
import com.example.sys.dto.UserCreateDTO;
import com.example.sys.dto.UserUpdateDTO;
import com.example.sys.dto.UserVO;
import com.example.sys.service.LoginSecurityService;
import com.example.sys.service.UserService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

/** UserController API 集成测试：验证请求 → 服务调用 → Result 响应的完整链路。 */
@DisplayName("UserController 请求-响应链路")
class UserControllerTest {

  private final UserService userService = mock(UserService.class);
  private final LoginSecurityService loginSecurityService = mock(LoginSecurityService.class);
  private final UserController controller = new UserController(userService, loginSecurityService);

  @Test
  @DisplayName("list：分页查询返回 PageResult，list 字段含数据")
  void list_returnsPageResult() {
    Page<UserVO> page = new PageImpl<>(List.of(new UserVO()));
    when(userService.search(any(), any(), any(), any())).thenReturn(page);
    Result<PageResult<UserVO>> result = controller.list(null, null, null, 1, 10);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data().list()).hasSize(1);
  }

  @Test
  @DisplayName("get：按 ID 返回用户")
  void get_returnsUser() {
    when(userService.getById(1L)).thenReturn(new UserVO());
    Result<UserVO> result = controller.get(1L);
    assertThat(result.isSuccess()).isTrue();
    verify(userService).getById(1L);
  }

  @Test
  @DisplayName("create：返回新用户 ID，并透传 DTO")
  void create_returnsId() {
    when(userService.create(any(UserCreateDTO.class))).thenReturn(5L);
    Result<Long> result = controller.create(new UserCreateDTO());
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data()).isEqualTo(5L);
  }

  @Test
  @DisplayName("update：调用服务更新并透传 ID 与 DTO")
  void update_invokesService() {
    Result<Void> result = controller.update(1L, new UserUpdateDTO());
    assertThat(result.isSuccess()).isTrue();
    verify(userService).update(eq(1L), any(UserUpdateDTO.class));
  }

  @Test
  @DisplayName("delete：调用服务删除并透传 ID")
  void delete_invokesService() {
    Result<Void> result = controller.delete(1L);
    assertThat(result.isSuccess()).isTrue();
    verify(userService).delete(1L);
  }

  @Test
  @DisplayName("deleteBatch：调用服务批量删除并透传 ID 列表")
  void deleteBatch_invokesService() {
    Result<Void> result = controller.deleteBatch(List.of(1L, 2L));
    assertThat(result.isSuccess()).isTrue();
    verify(userService).deleteBatch(List.of(1L, 2L));
  }

  @Test
  @DisplayName("assignRoles：调用服务分配角色并透传 ID 列表")
  void assignRoles_invokesService() {
    Result<Void> result = controller.assignRoles(1L, List.of(3L));
    assertThat(result.isSuccess()).isTrue();
    verify(userService).assignRoles(1L, List.of(3L));
  }

  @Test
  @DisplayName("getUserRoles：返回角色 ID 列表")
  void getUserRoles_returnsIds() {
    when(userService.getUserRoleIds(1L)).thenReturn(List.of(3L, 4L));
    Result<List<Long>> result = controller.getUserRoles(1L);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data()).containsExactly(3L, 4L);
  }

  @Test
  @DisplayName("resetPassword：调用服务重置密码并透传新密码")
  void resetPassword_invokesService() {
    Result<Void> result = controller.resetPassword(1L, "newpass123");
    assertThat(result.isSuccess()).isTrue();
    verify(userService).resetPassword(1L, "newpass123");
  }

  @Test
  @DisplayName("unlock：按用户名调用登录安全服务解锁")
  void unlock_invokesLoginSecurity() {
    when(userService.getEntityById(1L))
        .thenReturn(SysUser.builder().id(1L).username("bob").build());
    Result<Void> result = controller.unlock(1L);
    assertThat(result.isSuccess()).isTrue();
    verify(loginSecurityService).unlock("bob");
  }
}
