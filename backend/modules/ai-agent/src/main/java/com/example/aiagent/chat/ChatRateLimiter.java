package com.example.aiagent.chat;

import com.example.aiagent.config.AgentProperties;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * AI 对话限流：每用户固定时间窗口计数（内存实现，单实例足够；多实例部署需改用 Redis）。
 *
 * <p>超出 {@link AgentProperties.RateLimit#getMaxRequests()} 即拒绝，避免 DeepSeek 调用被刷导致成本失控。
 */
@Component
public class ChatRateLimiter {

  private final AgentProperties properties;
  private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();

  public ChatRateLimiter(AgentProperties properties) {
    this.properties = properties;
  }

  /**
   * 尝试为用户获取一次请求额度。
   *
   * @return true 放行；false 表示已达上限。{@code maxRequests<=0} 表示不限流，恒返回 true。
   */
  public boolean tryAcquire(Long userId) {
    int max = properties.getRateLimit().getMaxRequests();
    if (max <= 0) {
      return true;
    }
    long windowMs = Math.max(1, properties.getRateLimit().getWindowSeconds()) * 1000L;
    long now = System.currentTimeMillis();
    Window w =
        windows.compute(
            userId,
            (id, existing) -> {
              if (existing == null || now - existing.start >= windowMs) {
                Window nw = new Window();
                nw.start = now;
                nw.count = 1L;
                return nw;
              }
              existing.count++;
              return existing;
            });
    return w.count <= max;
  }

  /** 单用户的窗口计数状态。 */
  private static final class Window {
    volatile long start;
    volatile long count;
  }
}
