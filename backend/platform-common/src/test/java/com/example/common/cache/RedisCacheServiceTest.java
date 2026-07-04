package com.example.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Redis 缓存服务（mocked）")
class RedisCacheServiceTest {

  @Mock private RedisTemplate<String, Object> redisTemplate;
  @Mock private ValueOperations<String, Object> valueOps;
  @InjectMocks private RedisCacheService service;

  @BeforeEach
  void setUp() {
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
  }

  @Test
  @DisplayName("set(key,value)：委托 ValueOperations.set")
  void set_withoutTtl_delegatesToSet() {
    service.set("k", "v");
    verify(valueOps).set("k", "v");
  }

  @Test
  @DisplayName("set(key,value,ttl)：委托带 TTL 的 set")
  void set_withTtl_delegatesToSetWithTtl() {
    Duration ttl = Duration.ofSeconds(30);
    service.set("k", "v", ttl);
    verify(valueOps).set("k", "v", ttl);
  }

  @Test
  @DisplayName("get：值存在且类型匹配 → 返回值")
  void get_returnsValue_whenTypeMatches() {
    when(valueOps.get("k")).thenReturn("hello");
    Optional<String> result = service.get("k", String.class);
    assertThat(result).contains("hello");
  }

  @Test
  @DisplayName("get：值为 null → empty")
  void get_returnsEmpty_whenMissing() {
    when(valueOps.get("k")).thenReturn(null);
    assertThat(service.get("k", String.class)).isEmpty();
  }

  @Test
  @DisplayName("get：类型不匹配 → empty（不抛 ClassCastException）")
  void get_returnsEmpty_whenTypeMismatch() {
    when(valueOps.get("k")).thenReturn(123);
    assertThat(service.get("k", String.class)).isEmpty();
  }

  @Test
  @DisplayName("delete：委托 RedisTemplate.delete")
  void delete_delegates() {
    when(redisTemplate.delete("k")).thenReturn(true);
    assertThat(service.delete("k")).isTrue();
  }

  @Test
  @DisplayName("exists：hasKey=true → true")
  void exists_true_whenHasKey() {
    when(redisTemplate.hasKey("k")).thenReturn(true);
    assertThat(service.exists("k")).isTrue();
  }

  @Test
  @DisplayName("exists：hasKey=null → false（防御 null）")
  void exists_false_whenHasKeyNull() {
    when(redisTemplate.hasKey("k")).thenReturn(null);
    assertThat(service.exists("k")).isFalse();
  }

  @Test
  @DisplayName("incr：连续调用返回 1,2,3（验证委托 increment，非 get-then-set）")
  void incr_isAtomic_increment() {
    when(valueOps.increment("counter")).thenReturn(1L, 2L, 3L);
    assertThat(service.incr("counter")).isEqualTo(1L);
    assertThat(service.incr("counter")).isEqualTo(2L);
    assertThat(service.incr("counter")).isEqualTo(3L);
  }

  @Test
  @DisplayName("incr：increment 返回 null → 返回 0（防御）")
  void incr_returnsZero_whenNull() {
    when(valueOps.increment("k")).thenReturn(null);
    assertThat(service.incr("k")).isZero();
  }

  @Test
  @DisplayName("incr(key,delta)：委托 increment(key,delta)")
  void incr_withDelta_delegates() {
    when(valueOps.increment(eq("counter"), anyLong())).thenReturn(5L);
    assertThat(service.incr("counter", 5L)).isEqualTo(5L);
  }

  @Test
  @DisplayName("expire：委托 RedisTemplate.expire")
  void expire_delegates() {
    Duration ttl = Duration.ofMinutes(1);
    when(redisTemplate.expire("k", ttl)).thenReturn(true);
    assertThat(service.expire("k", ttl)).isTrue();
  }

  @Test
  @DisplayName("keys：委托 RedisTemplate.keys(pattern)")
  void keys_delegates() {
    Set<String> matched = Set.of("a", "b");
    when(redisTemplate.keys("prefix:*")).thenReturn(matched);
    assertThat(service.keys("prefix:*")).containsExactlyInAnyOrder("a", "b");
  }

  @Test
  @DisplayName("全链路：set → get → delete → exists=false")
  void fullLifecycle() {
    when(valueOps.get("user:1")).thenReturn("alice");
    when(redisTemplate.hasKey("user:1")).thenReturn(false);

    Optional<String> got = service.get("user:1", String.class);
    assertThat(got).contains("alice");
    service.delete("user:1");
    assertThat(service.exists("user:1")).isFalse();

    verify(valueOps).get("user:1");
    verify(redisTemplate).delete("user:1");
    verify(redisTemplate).hasKey("user:1");
  }
}
