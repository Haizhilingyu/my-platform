package com.example.openapp.webhook;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;

/**
 * 监听 HTTP 会话销毁事件，触发登出 webhook。
 *
 * <p>{@link HttpSessionDestroyedEvent} 由 {@code HttpSessionEventPublisher}（在 {@link WebhookConfig}
 * 中注册）在会话失效时发布， 携带会话中缓存的 {@link SecurityContext} 列表。从中提取认证主体（principal）调用 {@link
 * LogoutWebhookService}。
 *
 * <p>多副本部署下，任一副本销毁 Redis 中的会话后，该副本触发 webhook 推送。由于 webhook 查询的是共享的 {@code oauth_authorization}
 * 表，推送范围对所有副本一致。
 */
@Component
public class LogoutEventListener {

  private static final Logger log = LoggerFactory.getLogger(LogoutEventListener.class);

  private final LogoutWebhookService webhookService;

  public LogoutEventListener(LogoutWebhookService webhookService) {
    this.webhookService = webhookService;
  }

  @EventListener
  public void onSessionDestroyed(HttpSessionDestroyedEvent event) {
    List<SecurityContext> contexts = event.getSecurityContexts();
    if (contexts == null || contexts.isEmpty()) {
      return;
    }
    for (SecurityContext context : contexts) {
      Authentication authentication = context.getAuthentication();
      if (authentication == null || authentication.getName() == null) {
        continue;
      }
      String principalName = authentication.getName();
      log.debug(
          "Session {} destroyed for principal {}, firing logout webhooks",
          event.getId(),
          principalName);
      webhookService.fireLogoutWebhooks(principalName, principalName);
    }
  }
}
