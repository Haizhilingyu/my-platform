package com.example.openapp.webhook;

import com.example.openapp.client.JdbcRegisteredClientRepository;
import com.example.openapp.client.JdbcRegisteredClientRepository.ClientLogoutWebhook;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * 用户登出时向外部应用推送 webhook。
 *
 * <p>查询 {@code oauth_authorization} 表中该用户有活跃授权、且在 {@code openapp_client} 配置了 {@code
 * logout_webhook_url} 的客户端，向每个 webhook URL POST {@code {event:"logout", userId, username,
 * timestamp}}。单个 webhook 失败（网络超时 / 非 2xx / URL 无效）不影响其他客户端的推送，异常仅记录日志。
 *
 * <p>设计为同步推送 + 异常隔离：登出流程不阻塞在慢速 webhook 上（RestTemplate 超时由调用方注入），单个失败不影响整体登出语义。
 */
public class LogoutWebhookService {

  private static final Logger log = LoggerFactory.getLogger(LogoutWebhookService.class);

  private final JdbcRegisteredClientRepository clientRepository;
  private final RestTemplate restTemplate;

  public LogoutWebhookService(
      JdbcRegisteredClientRepository clientRepository, RestTemplate restTemplate) {
    this.clientRepository = clientRepository;
    this.restTemplate = restTemplate;
  }

  /**
   * 向指定用户的所有活跃授权客户端推送登出 webhook。
   *
   * @param principalName 用户标识（与 oauth_authorization.principal_name 一致）
   * @param username 用户名（推送到 webhook payload 的 username 字段，若为 null 回退为 principalName）
   * @return 实际推送成功的 webhook 数量（用于测试断言与可观测性）
   */
  public int fireLogoutWebhooks(String principalName, String username) {
    List<ClientLogoutWebhook> webhooks = clientRepository.findActiveLogoutWebhooks(principalName);
    if (webhooks.isEmpty()) {
      return 0;
    }
    String effectiveUsername = username != null ? username : principalName;
    int successCount = 0;
    for (ClientLogoutWebhook webhook : webhooks) {
      if (postWebhook(webhook, principalName, effectiveUsername)) {
        successCount++;
      }
    }
    return successCount;
  }

  private boolean postWebhook(ClientLogoutWebhook webhook, String principalName, String username) {
    Map<String, Object> payload =
        Map.of(
            "event",
            "logout",
            "userId",
            principalName,
            "username",
            username,
            "timestamp",
            Instant.now().toString());
    try {
      ResponseEntity<String> response =
          restTemplate.postForEntity(webhook.webhookUrl(), payload, String.class);
      if (response.getStatusCode().is2xxSuccessful()) {
        return true;
      }
      log.warn(
          "Logout webhook to client {} returned non-2xx status {} at {}",
          webhook.clientId(),
          response.getStatusCode(),
          webhook.webhookUrl());
      return false;
    } catch (Exception e) {
      log.warn(
          "Logout webhook to client {} failed at {}: {}",
          webhook.clientId(),
          webhook.webhookUrl(),
          e.getMessage());
      return false;
    }
  }
}
