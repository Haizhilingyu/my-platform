package com.example.openapp.client;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

/**
 * 基于 {@code openapp_client} 表的 RegisteredClientRepository。
 *
 * <p>集合字段（redirectUris/scopes/grantTypes/authMethods）以逗号分隔 TEXT 存储，H2+PG 兼容。 RegisteredClient.id 复用
 * clientId，简化映射。client_secret 存 BCrypt 哈希。
 */
public class JdbcRegisteredClientRepository implements RegisteredClientRepository {

  private static final RowMapper<RegisteredClient> MAPPER = (rs, rowNum) -> mapRow(rs);

  private final JdbcTemplate jdbc;

  public JdbcRegisteredClientRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void save(RegisteredClient registeredClient) {
    Integer existing =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM openapp_client WHERE client_id = ?",
            Integer.class,
            registeredClient.getClientId());
    if (existing != null && existing > 0) {
      update(registeredClient);
    } else {
      insert(registeredClient, null);
    }
  }

  @Override
  public RegisteredClient findById(String id) {
    List<RegisteredClient> result =
        jdbc.query(
            "SELECT client_id, client_secret, redirect_uris, post_logout_redirect_uris, "
                + "scopes, grant_types, authentication_methods, enabled "
                + "FROM openapp_client WHERE client_id = ?",
            MAPPER,
            id);
    return result.isEmpty() ? null : result.get(0);
  }

  @Override
  public RegisteredClient findByClientId(String clientId) {
    return findById(clientId);
  }

  /** 管理 API 创建：携带 client_name。 */
  public void insert(RegisteredClient registeredClient, String clientName) {
    jdbc.update(
        "INSERT INTO openapp_client "
            + "(client_id, client_secret, client_name, redirect_uris, "
            + "post_logout_redirect_uris, scopes, grant_types, "
            + "authentication_methods, enabled) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        registeredClient.getClientId(),
        registeredClient.getClientSecret(),
        clientName,
        toCsv(registeredClient.getRedirectUris()),
        toCsv(registeredClient.getPostLogoutRedirectUris()),
        toCsv(registeredClient.getScopes()),
        toCsvGrantTypes(registeredClient.getAuthorizationGrantTypes()),
        toCsvAuthMethods(registeredClient.getClientAuthenticationMethods()),
        true);
  }

  /** 管理 API 更新：保留 client_secret 与 client_name（单独管理）。 */
  public void update(RegisteredClient registeredClient) {
    jdbc.update(
        "UPDATE openapp_client SET "
            + "redirect_uris = ?, post_logout_redirect_uris = ?, scopes = ?, "
            + "grant_types = ?, authentication_methods = ?, enabled = ? "
            + "WHERE client_id = ?",
        toCsv(registeredClient.getRedirectUris()),
        toCsv(registeredClient.getPostLogoutRedirectUris()),
        toCsv(registeredClient.getScopes()),
        toCsvGrantTypes(registeredClient.getAuthorizationGrantTypes()),
        toCsvAuthMethods(registeredClient.getClientAuthenticationMethods()),
        true,
        registeredClient.getClientId());
  }

  public void updateSecret(String clientId, String bcryptHash) {
    jdbc.update(
        "UPDATE openapp_client SET client_secret = ? WHERE client_id = ?", bcryptHash, clientId);
  }

  public void deleteByClientId(String clientId) {
    jdbc.update("DELETE FROM openapp_client WHERE client_id = ?", clientId);
  }

  public String findNameByClientId(String clientId) {
    List<String> result =
        jdbc.queryForList(
            "SELECT client_name FROM openapp_client WHERE client_id = ?", String.class, clientId);
    return result.isEmpty() ? null : result.get(0);
  }

  /**
   * 查询某用户的活跃授权所涉及、且配置了登出 webhook 的客户端列表。
   *
   * <p>JOIN {@code oauth_authorization}（用户在该 client 上有授权记录）与 {@code openapp_client}（client 配置了 webhook
   * URL）。结果按 client_id 去重——一个用户可能在同一 client 上有多条授权记录（不同 grant_type），但 webhook 只需推送一次。
   *
   * @param principalName 用户标识（对应 oauth_authorization.principal_name）
   * @return 去重后的 (clientId, webhookUrl) 列表，无匹配时返回空列表
   */
  public List<ClientLogoutWebhook> findActiveLogoutWebhooks(String principalName) {
    return jdbc.query(
        "SELECT DISTINCT c.client_id, c.logout_webhook_url "
            + "FROM openapp_client c "
            + "JOIN oauth_authorization a ON a.registered_client_id = c.client_id "
            + "WHERE a.principal_name = ? AND c.logout_webhook_url IS NOT NULL "
            + "AND TRIM(c.logout_webhook_url) <> ''",
        (rs, rowNum) ->
            new ClientLogoutWebhook(
                rs.getString("client_id"), rs.getString("logout_webhook_url")),
        principalName);
  }

  /** 登出 webhook 目标（client_id + webhook URL）。 */
  public record ClientLogoutWebhook(String clientId, String webhookUrl) {}

  private static RegisteredClient mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
    String clientId = rs.getString("client_id");
    RegisteredClient.Builder builder =
        RegisteredClient.withId(clientId)
            .clientId(clientId)
            .clientSecret(rs.getString("client_secret"))
            .clientSettings(ClientSettings.builder().build())
            .tokenSettings(TokenSettings.builder().build());
    fromCsv(rs.getString("redirect_uris")).forEach(builder::redirectUri);
    fromCsv(rs.getString("post_logout_redirect_uris")).forEach(builder::postLogoutRedirectUri);
    fromCsv(rs.getString("scopes")).forEach(builder::scope);
    fromCsv(rs.getString("grant_types"))
        .forEach(g -> builder.authorizationGrantType(new AuthorizationGrantType(g)));
    fromCsv(rs.getString("authentication_methods"))
        .forEach(m -> builder.clientAuthenticationMethod(resolveMethod(m)));
    return builder.build();
  }

  private static ClientAuthenticationMethod resolveMethod(String value) {
    if ("none".equalsIgnoreCase(value)) {
      return ClientAuthenticationMethod.NONE;
    }
    return new ClientAuthenticationMethod(value);
  }

  private static String toCsv(Set<String> values) {
    if (values == null || values.isEmpty()) {
      return "";
    }
    return String.join(",", values);
  }

  private static String toCsvGrantTypes(Set<AuthorizationGrantType> grantTypes) {
    if (grantTypes == null || grantTypes.isEmpty()) {
      return "";
    }
    return grantTypes.stream()
        .map(AuthorizationGrantType::getValue)
        .collect(Collectors.joining(","));
  }

  private static String toCsvAuthMethods(Set<ClientAuthenticationMethod> methods) {
    if (methods == null || methods.isEmpty()) {
      return "";
    }
    return methods.stream()
        .map(ClientAuthenticationMethod::getValue)
        .collect(Collectors.joining(","));
  }

  private static Set<String> fromCsv(String csv) {
    if (csv == null || csv.isBlank()) {
      return new HashSet<>();
    }
    return new HashSet<>(Arrays.asList(csv.split(",")));
  }
}
