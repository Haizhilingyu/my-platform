package com.example.sys.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.cache.RedisCacheService;
import com.example.common.login.LoginSuccessEvent;
import com.example.sys.dto.SessionInfo;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

@DisplayName("SessionService 在线会话管理")
class SessionServiceTest {

  private static final Duration TTL = Duration.ofHours(1);

  private final RedisCacheService redisCacheService = mock(RedisCacheService.class);

  @SuppressWarnings("unchecked")
  private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);

  @SuppressWarnings("unchecked")
  private final SetOperations<String, Object> setOps = mock(SetOperations.class);

  private final SessionService service = new SessionService(redisCacheService, redisTemplate, TTL);

  private LoginSuccessEvent event(String userAgent) {
    return new LoginSuccessEvent(
        1L, "admin", "jti-123", "10.0.0.1", userAgent, LocalDateTime.now());
  }

  @Nested
  @DisplayName("recordSession 登录记录")
  class RecordSession {

    @Test
    @DisplayName("写入 session:active:{jti}（TTL=配置有效期）+ SADD 用户索引 + 刷新索引TTL")
    void recordSession_storesSessionAndIndex() {
      when(redisTemplate.opsForSet()).thenReturn(setOps);

      service.recordSession(event("Mozilla/5.0 Chrome/120.0"));

      ArgumentCaptor<SessionInfo> captor = ArgumentCaptor.forClass(SessionInfo.class);
      verify(redisCacheService)
          .set(eq(SessionService.SESSION_KEY_PREFIX + "jti-123"), captor.capture(), eq(TTL));
      SessionInfo stored = captor.getValue();
      assertThat(stored.jti()).isEqualTo("jti-123");
      assertThat(stored.userId()).isEqualTo(1L);
      assertThat(stored.username()).isEqualTo("admin");
      assertThat(stored.ip()).isEqualTo("10.0.0.1");
      assertThat(stored.deviceType()).isEqualTo("Chrome");

      verify(setOps).add(SessionService.USER_SESSIONS_KEY_PREFIX + 1L, "jti-123");
      verify(redisTemplate).expire(SessionService.USER_SESSIONS_KEY_PREFIX + 1L, TTL);
    }
  }

  @Nested
  @DisplayName("listSessions 会话列表")
  class ListSessions {

    @Test
    @DisplayName("返回用户所有活跃会话，清理已过期的索引残留")
    void listSessions_returnsActiveAndCleansStale() {
      when(redisTemplate.opsForSet()).thenReturn(setOps);
      String userKey = SessionService.USER_SESSIONS_KEY_PREFIX + 1L;
      when(setOps.members(userKey)).thenReturn(Set.of("jti-1", "jti-2"));

      SessionInfo info1 =
          new SessionInfo(
              "jti-1",
              1L,
              "admin",
              "10.0.0.1",
              "Chrome",
              "Chrome",
              LocalDateTime.now(),
              LocalDateTime.now().plusHours(1));
      when(redisCacheService.get(SessionService.SESSION_KEY_PREFIX + "jti-1", SessionInfo.class))
          .thenReturn(Optional.of(info1));
      when(redisCacheService.get(SessionService.SESSION_KEY_PREFIX + "jti-2", SessionInfo.class))
          .thenReturn(Optional.empty());

      var result = service.listSessions(1L);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).jti()).isEqualTo("jti-1");
      verify(setOps).remove(eq(userKey), eq("jti-2"));
    }

    @Test
    @DisplayName("无索引 → 空列表")
    void listSessions_emptyIndex() {
      when(redisTemplate.opsForSet()).thenReturn(setOps);
      when(setOps.members(any())).thenReturn(Set.of());

      var result = service.listSessions(99L);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("revokeSession 撤销/踢出")
  class RevokeSession {

    @Test
    @DisplayName("写入黑名单（TTL=剩余有效期）+ 删除会话 + 移除索引")
    void revokeSession_blacklistsAndDeletes() {
      when(redisTemplate.opsForSet()).thenReturn(setOps);
      SessionInfo info =
          new SessionInfo(
              "jti-1",
              1L,
              "admin",
              "10.0.0.1",
              "Chrome",
              "Chrome",
              LocalDateTime.now(),
              LocalDateTime.now().plusHours(1));
      when(redisCacheService.get(SessionService.SESSION_KEY_PREFIX + "jti-1", SessionInfo.class))
          .thenReturn(Optional.of(info));
      when(redisTemplate.getExpire(SessionService.SESSION_KEY_PREFIX + "jti-1")).thenReturn(3500L);

      service.revokeSession("jti-1");

      verify(redisCacheService)
          .set(eq(SessionService.BLACKLIST_KEY_PREFIX + "jti-1"), eq("1"), any(Duration.class));
      verify(redisCacheService).delete(SessionService.SESSION_KEY_PREFIX + "jti-1");
      verify(setOps).remove(SessionService.USER_SESSIONS_KEY_PREFIX + 1L, "jti-1");
    }

    @Test
    @DisplayName("会话不存在 → 幂等，不操作 Redis")
    void revokeSession_notFound_idempotent() {
      when(redisCacheService.get(any(), eq(SessionInfo.class))).thenReturn(Optional.empty());

      service.revokeSession("ghost-jti");

      verify(redisCacheService, never()).set(any(), any(), any(Duration.class));
      verify(redisCacheService, never()).delete(any());
    }

    @Test
    @DisplayName("剩余 TTL 为 0/负 → 不写黑名单（token 已过期）但仍清理记录")
    void revokeSession_zeroTtl_skipsBlacklist() {
      when(redisTemplate.opsForSet()).thenReturn(setOps);
      SessionInfo info =
          new SessionInfo(
              "jti-2",
              2L,
              "user",
              "10.0.0.2",
              "FF",
              "Firefox",
              LocalDateTime.now(),
              LocalDateTime.now());
      when(redisCacheService.get(SessionService.SESSION_KEY_PREFIX + "jti-2", SessionInfo.class))
          .thenReturn(Optional.of(info));
      when(redisTemplate.getExpire(SessionService.SESSION_KEY_PREFIX + "jti-2")).thenReturn(0L);

      service.revokeSession("jti-2");

      verify(redisCacheService, never()).set(any(), any(), any(Duration.class));
      verify(redisCacheService).delete(SessionService.SESSION_KEY_PREFIX + "jti-2");
    }
  }

  @Nested
  @DisplayName("parseDeviceType 设备类型解析")
  class DeviceTypeParsing {

    @Test
    void chrome() {
      assertThat(
              SessionService.parseDeviceType(
                  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0"))
          .isEqualTo("Chrome");
    }

    @Test
    void firefox() {
      assertThat(SessionService.parseDeviceType("Mozilla/5.0 Firefox/121.0")).isEqualTo("Firefox");
    }

    @Test
    void safari() {
      assertThat(
              SessionService.parseDeviceType(
                  "Mozilla/5.0 (Macintosh) Version/17.1 Safari/605.1.15"))
          .isEqualTo("Safari");
    }

    @Test
    void edge() {
      assertThat(SessionService.parseDeviceType("Mozilla/5.0 Edg/120.0.0.0")).isEqualTo("Edge");
    }

    @Test
    void mobile() {
      assertThat(
              SessionService.parseDeviceType(
                  "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0) Mobile/15E148"))
          .isEqualTo("Mobile");
    }

    @Test
    void postman() {
      assertThat(SessionService.parseDeviceType("PostmanRuntime/7.36.0")).isEqualTo("Postman");
    }

    @Test
    void unknown() {
      assertThat(SessionService.parseDeviceType("curl/8.1.2")).isEqualTo("Unknown");
      assertThat(SessionService.parseDeviceType(null)).isEqualTo("Unknown");
      assertThat(SessionService.parseDeviceType("")).isEqualTo("Unknown");
    }
  }
}
