package com.example.openapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Spring Session Redis 配置：HTTP 会话存储到 Redis。
 *
 * <p>这是 OIDC RP-Initiated Logout 实现高可用（HA）的基础。授权服务器（AS）在多副本部署时，任一副本收到
 * {@code /oauth2/logout} 请求并销毁会话后，该销毁操作作用于 Redis 中的共享会话——所有副本立即感知，避免出现「A 副本登出但 B
 * 副本仍持有有效会话」的单点残留。
 *
 * <p>{@link ConfigureRedisAction#NO_OP} 禁用 Spring Session 自动配置 Redis keyspace 通知（{@code CONFIG SET
 * notify-keyspace-events}）。原因：
 *
 * <ol>
 *   <li>托管 Redis（AWS ElastiCache / 阿里云 Redis）通常禁用 {@code CONFIG} 命令，自动配置会抛错导致上下文启动失败。
 *   <li>登出场景下会话销毁是同步的（{@code HttpSession.invalidate()}），无需 keyspace 事件感知过期。
 *   <li>测试环境（{@code application-test.yml}）Redis 指向 localhost:6379 但无实例运行，NO_OP 跳过连接尝试，
 *       保证 {@code @SpringBootTest} 上下文正常加载（符合「gracefully handle test profile」要求）。
 * </ol>
 *
 * <p>如需会话过期事件（如 T11 在线会话的 TTL 过期感知），运维方在 Redis 中手动执行 {@code CONFIG SET
 * notify-keyspace-events Elg} 即可，Spring Session 会自动订阅。
 */
@Configuration
@EnableRedisHttpSession
public class SessionConfig {

  /**
   * 禁用 Spring Session 的 Redis keyspace 自动配置。
   *
   * @return 永不执行的 {@link ConfigureRedisAction}，避免启动时主动连 Redis 发送 CONFIG 命令
   */
  @Bean
  public ConfigureRedisAction configureRedisAction() {
    return ConfigureRedisAction.NO_OP;
  }
}
