package com.example.aiagent.chat;

import com.example.aiagent.config.AgentProperties;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * AI 对话限流：每用户固定时间窗口计数。
 *
 * <p><b>多实例部署</b>：计数落在 Redis（Lua 原子 {@code INCR + EXPIRE}），多个应用实例共享同一计数。 当 Redis 不可达（连接被拒/超时，典型如本地裸
 * {@code spring-boot:run} 未启动 Redis），自动降级为 单实例内存计数（{@link InMemoryFallback}），保证 AI
 * 功能可用——只是此时跨实例不再联动。
 *
 * <p>超出 {@link AgentProperties.RateLimit#getMaxRequests()} 即拒绝，避免 DeepSeek 调用被刷导致成本失控。 {@code
 * maxRequests<=0} 表示不限流，恒返回 true 且不触碰 Redis。
 */
@Component
public class ChatRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(ChatRateLimiter.class);

  /** Redis 计数键前缀。完整键：{@code ai:ratelimit:{userId}}。 */
  private static final String KEY_PREFIX = "ai:ratelimit:";

  /**
   * 原子「自增并在首次自增时设置窗口过期」。
   *
   * <pre>
   * local c = redis.call('INCR', KEYS[1])
   * if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
   * return c
   * </pre>
   *
   * <p>用 Lua 保证 INCR 与 EXPIRE 在服务端单次往返内原子完成，避免「INCR 成功但进程崩溃导致 key 永不过期」。 ARGV[1] = 窗口秒数。
   */
  private static final RedisScript<Long> ACQUIRE_SCRIPT;

  static {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setScriptText(
        "local c = redis.call('INCR', KEYS[1]) "
            + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
            + "return c");
    script.setResultType(Long.class);
    ACQUIRE_SCRIPT = script;
  }

  private final AgentProperties properties;

  /**
   * 可为 {@code null}（单参数构造，测试/无 Redis 场景）：此时恒走 {@link InMemoryFallback}。 正常 Spring 上下文下由
   * platform-common 的 {@code RedisConfig} 注入，非空。
   */
  private final StringRedisTemplate redis;

  private final InMemoryFallback fallback = new InMemoryFallback();

  /**
   * Spring 构造：注入 Redis 模板（计数主路径）。
   *
   * <p>本类另有一个单参数的降级构造（无 Redis，测试/内存兜底），故存在多个构造函数。Spring 在面对多构造且无 {@code @Autowired}
   * 标注时会回退查找无参默认构造并报 {@code NoSuchMethodException}，导致 bean 创建失败、整个 应用上下文启动崩溃。在此显式标注，指明 Spring
   * 使用双参数构造注入。
   */
  @Autowired
  public ChatRateLimiter(AgentProperties properties, StringRedisTemplate redis) {
    this.properties = properties;
    this.redis = redis;
  }

  /** 测试/降级构造：无 Redis，恒使用内存计数。 */
  ChatRateLimiter(AgentProperties properties) {
    this(properties, null);
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
    int windowSeconds = Math.max(1, properties.getRateLimit().getWindowSeconds());
    if (redis == null) {
      return fallback.tryAcquire(userId, max, windowSeconds * 1000L);
    }
    try {
      Long count =
          redis.execute(
              ACQUIRE_SCRIPT, List.of(KEY_PREFIX + userId), String.valueOf(windowSeconds));
      return count != null && count <= max;
    } catch (RuntimeException e) {
      log.warn("Redis 限流不可用（{}），降级为单实例内存限流", e.toString());
      return fallback.tryAcquire(userId, max, windowSeconds * 1000L);
    }
  }

  /**
   * 内存兜底计数器：单实例固定时间窗口（Redis 不可达时使用）。
   *
   * <p>逻辑与 Redis 路径等价——窗口内首次访问初始化计数为 1 并记录起点，后续访问累加；窗口过期则重置。
   */
  static final class InMemoryFallback {

    private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();

    boolean tryAcquire(Long userId, int max, long windowMs) {
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

    private static final class Window {
      volatile long start;
      volatile long count;
    }
  }
}
