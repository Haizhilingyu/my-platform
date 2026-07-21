package com.example.aiagent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.aiagent.config.AgentProperties;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/** 限流器单测：覆盖三种路径——内存兜底（无 Redis）、Redis 主路径（mock 计数）、Redis 不可达时降级。 */
class ChatRateLimiterTest {

  private AgentProperties props(int max, int windowSeconds) {
    AgentProperties p = new AgentProperties();
    p.getRateLimit().setMaxRequests(max);
    p.getRateLimit().setWindowSeconds(windowSeconds);
    return p;
  }

  // ===== 内存兜底路径（无 Redis 注入，即降级构造）=====

  @Test
  void inMemoryAllowsUntilLimitThenBlocks() {
    var limiter = new ChatRateLimiter(props(3, 60));
    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isFalse();
  }

  @Test
  void inMemoryPerUserIsolated() {
    var limiter = new ChatRateLimiter(props(3, 60));
    IntStream.range(0, 3).forEach(i -> limiter.tryAcquire(1L));
    assertThat(limiter.tryAcquire(1L)).isFalse(); // 用户 1 已达上限
    assertThat(limiter.tryAcquire(2L)).isTrue(); // 用户 2 全新窗口
  }

  @Test
  void unlimitedWhenMaxZeroNeverTouchesRedis() {
    // max<=0 应恒放行且不调用 Redis（即便注入了也会提前 return）
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    var limiter = new ChatRateLimiter(props(0, 60), redis);
    IntStream.range(0, 100).forEach(i -> assertThat(limiter.tryAcquire(1L)).isTrue());
    verifyNoInteractions(redis);
  }

  @Test
  void inMemoryWindowResetsAfterExpiry() throws InterruptedException {
    var limiter = new ChatRateLimiter(props(2, 1)); // 1 秒窗口
    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isFalse(); // 窗口内达上限
    Thread.sleep(1_100L); // 等待窗口过期
    assertThat(limiter.tryAcquire(1L)).isTrue(); // 新窗口重新计数
  }

  // ===== Redis 主路径（mock StringRedisTemplate.execute 返回的计数）=====

  @Test
  void redisAllowsUntilLimitThenBlocks() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    // 模拟连续 INCR：1,2,3（放行）→4（拒绝）
    when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L, 2L, 3L, 4L);
    var limiter = new ChatRateLimiter(props(3, 60), redis);
    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isFalse();
  }

  @Test
  void redisFallsBackToInMemoryWhenConnectionThrows() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    when(redis.execute(any(RedisScript.class), anyList(), any()))
        .thenThrow(new RuntimeException("connection refused"));
    // Redis 抛异常 → 降级内存限流，内存逻辑仍生效
    var limiter = new ChatRateLimiter(props(2, 60), redis);
    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isFalse();
  }
}
