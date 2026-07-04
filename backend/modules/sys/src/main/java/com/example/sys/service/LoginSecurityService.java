package com.example.sys.service;

import com.example.common.cache.RedisCacheService;
import com.example.common.exception.AccountLockedException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 登录安全服务：基于 Redis 原子计数器的失败次数追踪 + 自动锁定。
 *
 * <p>失败计数 key: {@code login:fail:{username}}（首次失败设 30min TTL）。 锁定 key: {@code user:lock:{username}}
 * = "1"（TTL 同失败窗口）。{@code incr} 为 Redis INCR 原子操作，并发安全。
 *
 * <p>锁定阈值由 {@link ConfigService} 读取 {@code sys.security.login.max-fail-count}（默认 3）。
 *
 * <p>管理员豁免：内置 {@code admin} 账号不计失败、不被锁定，确保系统始终可管理（可解锁其他用户）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginSecurityService {

  static final String FAIL_KEY_PREFIX = "login:fail:";
  static final String LOCK_KEY_PREFIX = "user:lock:";
  static final Duration LOCK_WINDOW = Duration.ofMinutes(30);
  static final int DEFAULT_MAX_FAIL_COUNT = 3;
  static final String MAX_FAIL_CONFIG_KEY = "sys.security.login.max-fail-count";
  static final String ADMIN_USERNAME = "admin";

  private final RedisCacheService redisCacheService;
  private final ConfigService configService;

  /** 若账号已锁定则抛 {@link AccountLockedException}（HTTP 423）。 */
  public void checkLockStatus(String username) {
    if (username == null || username.isBlank()) {
      return;
    }
    if (isLocked(username)) {
      throw AccountLockedException.defaultMessage();
    }
  }

  /**
   * 记录一次失败登录。原子自增失败计数，首次失败设窗口 TTL；达到阈值则写入锁定 key。
   *
   * <p>管理员账号豁免（不计失败），保证系统可管理性。
   */
  public void recordFailedAttempt(String username) {
    if (username == null || username.isBlank() || isAdminExempt(username)) {
      return;
    }
    long count = redisCacheService.incr(failKey(username));
    if (count == 1) {
      redisCacheService.expire(failKey(username), LOCK_WINDOW);
    }
    int threshold = resolveThreshold();
    if (count >= threshold) {
      redisCacheService.set(lockKey(username), "1", LOCK_WINDOW);
      log.warn("账号锁定: {} 失败 {} 次达到阈值 {}", username, count, threshold);
    }
  }

  /** 登录成功后清除失败计数（锁定 key 不影响——成功意味着未被锁）。 */
  public void recordSuccessfulLogin(String username) {
    if (username == null || username.isBlank()) {
      return;
    }
    redisCacheService.delete(failKey(username));
  }

  /** 管理员解锁：同时清除锁定 key 与失败计数。 */
  public void unlock(String username) {
    if (username == null || username.isBlank()) {
      return;
    }
    redisCacheService.delete(lockKey(username));
    redisCacheService.delete(failKey(username));
    log.info("账号解锁: {}", username);
  }

  public boolean isLocked(String username) {
    return redisCacheService.exists(lockKey(username));
  }

  public int getFailCount(String username) {
    return redisCacheService.get(failKey(username), Long.class).map(Long::intValue).orElse(0);
  }

  private int resolveThreshold() {
    try {
      String value =
          configService.getValue(MAX_FAIL_CONFIG_KEY, String.valueOf(DEFAULT_MAX_FAIL_COUNT));
      int parsed = Integer.parseInt(value.trim());
      return parsed > 0 ? parsed : DEFAULT_MAX_FAIL_COUNT;
    } catch (Exception e) {
      return DEFAULT_MAX_FAIL_COUNT;
    }
  }

  private boolean isAdminExempt(String username) {
    return ADMIN_USERNAME.equalsIgnoreCase(username);
  }

  private String failKey(String username) {
    return FAIL_KEY_PREFIX + username;
  }

  private String lockKey(String username) {
    return LOCK_KEY_PREFIX + username;
  }
}
