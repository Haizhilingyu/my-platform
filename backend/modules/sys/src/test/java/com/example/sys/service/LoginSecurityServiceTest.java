package com.example.sys.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.cache.RedisCacheService;
import com.example.common.exception.AccountLockedException;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 登录安全服务单元测试：基于 Redis 原子计数的失败锁定逻辑。 */
@ExtendWith(MockitoExtension.class)
@DisplayName("登录安全服务")
class LoginSecurityServiceTest {

  @Mock private RedisCacheService redisCacheService;
  @Mock private ConfigService configService;
  @InjectMocks private LoginSecurityService loginSecurityService;

  private static final String FAIL = LoginSecurityService.FAIL_KEY_PREFIX + "bob";
  private static final String LOCK = LoginSecurityService.LOCK_KEY_PREFIX + "bob";

  @Nested
  @DisplayName("锁定检查")
  class CheckLock {

    @Test
    @DisplayName("账号已锁定：抛 AccountLockedException（HTTP 423）")
    void should_throw_when_locked() {
      when(redisCacheService.exists(LOCK)).thenReturn(true);
      assertThatThrownBy(() -> loginSecurityService.checkLockStatus("bob"))
          .isInstanceOf(AccountLockedException.class)
          .satisfies(e -> assertThat(((AccountLockedException) e).getCode()).isEqualTo(423));
    }

    @Test
    @DisplayName("未锁定：正常通过")
    void should_pass_when_notLocked() {
      when(redisCacheService.exists(LOCK)).thenReturn(false);
      assertThatCode(() -> loginSecurityService.checkLockStatus("bob")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("空/空白用户名：直接返回，不查 Redis")
    void should_noop_when_blank() {
      loginSecurityService.checkLockStatus("  ");
      verify(redisCacheService, never()).exists(any());
    }
  }

  @Nested
  @DisplayName("记录失败登录")
  class RecordFailed {

    @Test
    @DisplayName("达到阈值：写入锁定 key，不再设窗口 TTL")
    void should_lock_when_thresholdReached() {
      when(configService.getValue(LoginSecurityService.MAX_FAIL_CONFIG_KEY, "3")).thenReturn("3");
      when(redisCacheService.incr(FAIL)).thenReturn(3L);

      loginSecurityService.recordFailedAttempt("bob");

      verify(redisCacheService).set(eq(LOCK), eq("1"), eq(LoginSecurityService.LOCK_WINDOW));
      verify(redisCacheService, never()).expire(eq(FAIL), any(Duration.class));
    }

    @Test
    @DisplayName("首次失败：设置 30min 窗口 TTL，未达阈值不锁")
    void should_setTtl_when_firstFailure() {
      when(configService.getValue(LoginSecurityService.MAX_FAIL_CONFIG_KEY, "3")).thenReturn("3");
      when(redisCacheService.incr(FAIL)).thenReturn(1L);

      loginSecurityService.recordFailedAttempt("bob");

      verify(redisCacheService).expire(eq(FAIL), eq(LoginSecurityService.LOCK_WINDOW));
      verify(redisCacheService, never()).set(eq(LOCK), any(), any(Duration.class));
    }

    @Test
    @DisplayName("管理员 admin 豁免：不计失败")
    void should_skip_admin() {
      loginSecurityService.recordFailedAttempt("admin");
      verify(redisCacheService, never()).incr(any());
    }

    @Test
    @DisplayName("管理员大小写变体（Admin/ADMIN）同样豁免：不计失败")
    void should_skip_admin_caseInsensitive() {
      loginSecurityService.recordFailedAttempt("Admin");
      verify(redisCacheService, never()).incr(any());
      loginSecurityService.recordFailedAttempt("ADMIN");
      verify(redisCacheService, never()).incr(any());
    }

    @Test
    @DisplayName("空用户名：直接返回")
    void should_noop_when_blank() {
      loginSecurityService.recordFailedAttempt("");
      verify(redisCacheService, never()).incr(any());
    }
  }

  @Nested
  @DisplayName("成功 / 解锁 / 状态")
  class Misc {

    @Test
    @DisplayName("成功登录：清除失败计数")
    void should_clearFail_when_success() {
      loginSecurityService.recordSuccessfulLogin("bob");
      verify(redisCacheService).delete(FAIL);
    }

    @Test
    @DisplayName("管理员解锁：清除锁定 key 与失败计数")
    void should_clearBoth_when_unlock() {
      loginSecurityService.unlock("bob");
      verify(redisCacheService).delete(LOCK);
      verify(redisCacheService).delete(FAIL);
    }

    @Test
    @DisplayName("isLocked 反映 Redis 状态")
    void should_reflectLockState() {
      when(redisCacheService.exists(LOCK)).thenReturn(true);
      assertThat(loginSecurityService.isLocked("bob")).isTrue();
    }

    @Test
    @DisplayName("getFailCount 读取计数，缺失为 0")
    void should_readFailCount() {
      when(redisCacheService.get(FAIL, Long.class)).thenReturn(Optional.of(5L));
      assertThat(loginSecurityService.getFailCount("bob")).isEqualTo(5);
      when(redisCacheService.get(FAIL, Long.class)).thenReturn(Optional.empty());
      assertThat(loginSecurityService.getFailCount("bob")).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("阈值解析")
  class Threshold {

    @Test
    @DisplayName("配置为合法整数：取配置值")
    void should_useConfiguredThreshold() {
      when(configService.getValue(LoginSecurityService.MAX_FAIL_CONFIG_KEY, "3")).thenReturn("5");
      when(redisCacheService.incr(FAIL)).thenReturn(5L);

      loginSecurityService.recordFailedAttempt("bob");

      verify(redisCacheService).set(eq(LOCK), eq("1"), any(Duration.class));
    }

    @Test
    @DisplayName("配置为非正数：回退默认 3")
    void should_fallback_when_nonPositive() {
      when(configService.getValue(LoginSecurityService.MAX_FAIL_CONFIG_KEY, "3")).thenReturn("0");
      when(redisCacheService.incr(FAIL)).thenReturn(3L);

      loginSecurityService.recordFailedAttempt("bob");

      verify(redisCacheService).set(eq(LOCK), eq("1"), any(Duration.class));
    }

    @Test
    @DisplayName("配置非法（非数字）：捕获异常回退默认 3")
    void should_fallback_when_invalid() {
      when(configService.getValue(LoginSecurityService.MAX_FAIL_CONFIG_KEY, "3")).thenReturn("abc");
      when(redisCacheService.incr(FAIL)).thenReturn(3L);

      loginSecurityService.recordFailedAttempt("bob");

      verify(redisCacheService).set(eq(LOCK), eq("1"), any(Duration.class));
    }
  }
}
