package com.example.common.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.common.exception.ForbiddenException;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** PermissionAspect 测试。 */
@ExtendWith(MockitoExtension.class)
@DisplayName("权限切面")
class PermissionAspectTest {

  @Mock private ProceedingJoinPoint joinPoint;
  @InjectMocks private PermissionAspect aspect;

  @Test
  @DisplayName("有权限：放行执行")
  void should_proceed_when_userHasPermission() throws Throwable {
    // Given
    var annotation = mock(RequiresPermission.class);
    when(annotation.value()).thenReturn(new String[] {"sys:user:add"});
    when(annotation.logical()).thenReturn(Logical.AND);
    CurrentUser.set(new CurrentUser.UserInfo(1L, "admin", null, Set.of(), Set.of("sys:user:add")));
    when(joinPoint.proceed()).thenReturn("OK");

    // When
    Object result = aspect.checkPermission(joinPoint, annotation);

    // Then
    assertThat(result).isEqualTo("OK");
    verify(joinPoint).proceed();
    CurrentUser.clear();
  }

  @Test
  @DisplayName("无权限：抛 ForbiddenException")
  void should_throwForbidden_when_noPermission() {
    // Given
    var annotation = mock(RequiresPermission.class);
    when(annotation.value()).thenReturn(new String[] {"sys:user:delete"});
    when(annotation.logical()).thenReturn(Logical.AND);
    CurrentUser.set(new CurrentUser.UserInfo(1L, "user1", null, Set.of(), Set.of("sys:user:list")));

    // When & Then
    assertThatThrownBy(() -> aspect.checkPermission(joinPoint, annotation))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("sys:user:delete");
    CurrentUser.clear();
  }

  @Test
  @DisplayName("OR 逻辑：拥有其中一个权限即放行")
  void should_proceed_when_hasAnyWithOrLogic() throws Throwable {
    // Given
    var annotation = mock(RequiresPermission.class);
    when(annotation.value()).thenReturn(new String[] {"sys:user:add", "sys:user:edit"});
    when(annotation.logical()).thenReturn(Logical.OR);
    CurrentUser.set(new CurrentUser.UserInfo(1L, "user1", null, Set.of(), Set.of("sys:user:edit")));
    when(joinPoint.proceed()).thenReturn("OK");

    // When
    Object result = aspect.checkPermission(joinPoint, annotation);

    // Then
    assertThat(result).isEqualTo("OK");
    CurrentUser.clear();
  }
}
