package com.example.sys.service;

import com.example.common.cache.RedisCacheService;
import com.example.common.login.LoginSuccessEvent;
import com.example.sys.dto.SessionInfo;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 在线会话管理服务。
 *
 * <p>Redis 结构：
 *
 * <ul>
 *   <li>{@code session:active:{jti}} → {@link SessionInfo} JSON，TTL = token 有效期
 *   <li>{@code session:user:{userId}} → SET{jti}，用户活跃会话索引
 *   <li>{@code jwt:blacklist:{jti}} → T10 黑名单（撤销时写入）
 * </ul>
 */
@Service
public class SessionService {

  static final String SESSION_KEY_PREFIX = "session:active:";
  static final String USER_SESSIONS_KEY_PREFIX = "session:user:";
  static final String BLACKLIST_KEY_PREFIX = "jwt:blacklist:";

  private static final Pattern CHROME = Pattern.compile("Chrome\\/([\\d.]+)");
  private static final Pattern FIREFOX = Pattern.compile("Firefox\\/([\\d.]+)");
  private static final Pattern SAFARI = Pattern.compile("Version\\/([\\d.]+).*Safari");
  private static final Pattern EDGE = Pattern.compile("Edg\\/([\\d.]+)");
  private static final Pattern MOBILE =
      Pattern.compile("Android|iPhone|iPod|Windows Phone|Mobile", Pattern.CASE_INSENSITIVE);
  private static final Pattern POSTMAN =
      Pattern.compile("PostmanRuntime", Pattern.CASE_INSENSITIVE);

  private final RedisCacheService redisCacheService;
  private final RedisTemplate<String, Object> redisTemplate;
  private final Duration sessionTtl;

  @Autowired
  public SessionService(
      RedisCacheService redisCacheService,
      RedisTemplate<String, Object> redisTemplate,
      @Value("${app.security.jwt.expiration:86400000}") long jwtExpirationMillis) {
    this.redisCacheService = redisCacheService;
    this.redisTemplate = redisTemplate;
    this.sessionTtl = Duration.ofMillis(jwtExpirationMillis);
  }

  SessionService(
      RedisCacheService redisCacheService,
      RedisTemplate<String, Object> redisTemplate,
      Duration sessionTtl) {
    this.redisCacheService = redisCacheService;
    this.redisTemplate = redisTemplate;
    this.sessionTtl = sessionTtl;
  }

  /** 记录登录会话。由 {@link SessionEventListener} 在收到 {@link LoginSuccessEvent} 时调用。 */
  public void recordSession(LoginSuccessEvent event) {
    String deviceType = parseDeviceType(event.userAgent());
    LocalDateTime expiresAt =
        LocalDateTime.ofInstant(
            event.loginTime().atZone(ZoneId.systemDefault()).toInstant().plus(sessionTtl),
            ZoneId.systemDefault());
    SessionInfo info =
        new SessionInfo(
            event.jti(),
            event.userId(),
            event.username(),
            event.ip(),
            event.userAgent(),
            deviceType,
            event.loginTime(),
            expiresAt);

    String sessionKey = SESSION_KEY_PREFIX + event.jti();
    String userKey = USER_SESSIONS_KEY_PREFIX + event.userId();

    redisCacheService.set(sessionKey, info, sessionTtl);
    redisTemplate.opsForSet().add(userKey, event.jti());
    redisTemplate.expire(userKey, sessionTtl);
  }

  /** 列出用户的所有活跃会话（自动清理已过期的索引残留）。 */
  public List<SessionInfo> listSessions(Long userId) {
    String userKey = USER_SESSIONS_KEY_PREFIX + userId;
    Set<Object> members = redisTemplate.opsForSet().members(userKey);
    if (members == null || members.isEmpty()) {
      return List.of();
    }

    List<SessionInfo> sessions = new ArrayList<>();
    List<String> stale = new ArrayList<>();
    for (Object member : members) {
      String jti = String.valueOf(member);
      Optional<SessionInfo> info =
          redisCacheService.get(SESSION_KEY_PREFIX + jti, SessionInfo.class);
      if (info.isPresent()) {
        sessions.add(info.get());
      } else {
        stale.add(jti);
      }
    }
    if (!stale.isEmpty()) {
      redisTemplate.opsForSet().remove(userKey, stale.toArray());
    }
    return sessions;
  }

  /** 获取单个会话。用于自撤销时的归属校验。 */
  public Optional<SessionInfo> getSession(String jti) {
    return redisCacheService.get(SESSION_KEY_PREFIX + jti, SessionInfo.class);
  }

  /**
   * 撤销会话：写入黑名单（使 token 立即失效）+ 删除会话记录 + 移除用户索引。
   *
   * <p>幂等：会话不存在时不操作。
   */
  public void revokeSession(String jti) {
    String sessionKey = SESSION_KEY_PREFIX + jti;
    Optional<SessionInfo> existing = redisCacheService.get(sessionKey, SessionInfo.class);
    if (existing.isEmpty()) {
      return;
    }

    SessionInfo info = existing.get();
    Long remainingSeconds = redisTemplate.getExpire(sessionKey);
    if (remainingSeconds != null && remainingSeconds > 0) {
      redisCacheService.set(BLACKLIST_KEY_PREFIX + jti, "1", Duration.ofSeconds(remainingSeconds));
    }

    redisCacheService.delete(sessionKey);
    redisTemplate.opsForSet().remove(USER_SESSIONS_KEY_PREFIX + info.userId(), jti);
  }

  /** 从 User-Agent 解析设备类型。 */
  public static String parseDeviceType(String userAgent) {
    if (userAgent == null || userAgent.isBlank()) {
      return "Unknown";
    }
    if (POSTMAN.matcher(userAgent).find()) {
      return "Postman";
    }
    if (MOBILE.matcher(userAgent).find()) {
      return "Mobile";
    }
    Matcher edge = EDGE.matcher(userAgent);
    if (edge.find()) {
      return "Edge";
    }
    Matcher chrome = CHROME.matcher(userAgent);
    if (chrome.find()) {
      return "Chrome";
    }
    Matcher firefox = FIREFOX.matcher(userAgent);
    if (firefox.find()) {
      return "Firefox";
    }
    Matcher safari = SAFARI.matcher(userAgent);
    if (safari.find()) {
      return "Safari";
    }
    return "Unknown";
  }
}
