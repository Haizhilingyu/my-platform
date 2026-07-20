package com.example.aiagent.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aiagent.config.AgentProperties;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** 限流器单测。 */
class ChatRateLimiterTest {

  private AgentProperties props(int max, int windowSeconds) {
    AgentProperties p = new AgentProperties();
    p.getRateLimit().setMaxRequests(max);
    p.getRateLimit().setWindowSeconds(windowSeconds);
    return p;
  }

  @Test
  void allowsUntilLimitThenBlocks() {
    var limiter = new ChatRateLimiter(props(3, 60));
    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isFalse();
  }

  @Test
  void perUserIsolated() {
    var limiter = new ChatRateLimiter(props(3, 60));
    IntStream.range(0, 3).forEach(i -> limiter.tryAcquire(1L));
    assertThat(limiter.tryAcquire(1L)).isFalse(); // 用户 1 已达上限
    assertThat(limiter.tryAcquire(2L)).isTrue(); // 用户 2 全新窗口
  }

  @Test
  void unlimitedWhenMaxZero() {
    var limiter = new ChatRateLimiter(props(0, 60));
    IntStream.range(0, 100).forEach(i -> assertThat(limiter.tryAcquire(1L)).isTrue());
  }

  @Test
  void windowResetsAfterExpiry() throws InterruptedException {
    var limiter = new ChatRateLimiter(props(2, 1)); // 1 秒窗口
    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isFalse(); // 窗口内达上限
    Thread.sleep(1_100L); // 等待窗口过期
    assertThat(limiter.tryAcquire(1L)).isTrue(); // 新窗口重新计数
  }
}
