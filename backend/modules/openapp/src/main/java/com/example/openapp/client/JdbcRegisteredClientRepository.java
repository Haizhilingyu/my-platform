package com.example.openapp.client;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
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
   * <p>JOIN {@code oauth_authorization}（用户在该 client 上有授权记录）与 {@code openapp_client}（client 配置了
   * webhook URL）。结果按 client_id 去重——一个用户可能在同一 client 上有多条授权记录（不同 grant_type），但 webhook 只需推送一次。
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
            new ClientLogoutWebhook(rs.getString("client_id"), rs.getString("logout_webhook_url")),
        principalName);
  }

  /** 登出 webhook 目标（client_id + webhook URL）。 */
  public record ClientLogoutWebhook(String clientId, String webhookUrl) {}

  /** 管理 API 视角的客户端行（包含 BIGSERIAL id、client_name、enabled、created_at 等 OAuth2 SDK 不关心的字段）。 */
  public record OpenAppClientRow(
      Long id,
      String clientId,
      String clientName,
      Set<String> redirectUris,
      Set<String> postLogoutRedirectUris,
      Set<String> scopes,
      Set<String> grantTypes,
      boolean enabled,
      Instant createdAt) {}

  private static final RowMapper<OpenAppClientRow> ROW_MAPPER =
      (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("created_at");
        return new OpenAppClientRow(
            rs.getLong("id"),
            rs.getString("client_id"),
            rs.getString("client_name"),
            fromCsv(rs.getString("redirect_uris")),
            fromCsv(rs.getString("post_logout_redirect_uris")),
            fromCsv(rs.getString("scopes")),
            fromCsv(rs.getString("grant_types")),
            rs.getBoolean("enabled"),
            ts == null ? null : ts.toInstant());
      };

  private static final String SELECT_ROW_COLUMNS =
      "id, client_id, client_name, redirect_uris, post_logout_redirect_uris, "
          + "scopes, grant_types, authentication_methods, enabled, created_at ";

  /** 按数据库 BIGSERIAL id 查询客户端管理行。 */
  public OpenAppClientRow findRowById(Long id) {
    List<OpenAppClientRow> rows =
        jdbc.query(
            "SELECT " + SELECT_ROW_COLUMNS + "FROM openapp_client WHERE id = ?", ROW_MAPPER, id);
    return rows.isEmpty() ? null : rows.get(0);
  }

  /** 按 client_id 查询客户端管理行。 */
  public OpenAppClientRow findRowByClientId(String clientId) {
    List<OpenAppClientRow> rows =
        jdbc.query(
            "SELECT " + SELECT_ROW_COLUMNS + "FROM openapp_client WHERE client_id = ?",
            ROW_MAPPER,
            clientId);
    return rows.isEmpty() ? null : rows.get(0);
  }

  /**
   * 分页 + 关键字搜索客户端管理行。
   *
   * @param keyword 关键字（client_id / client_name 模糊匹配），null 或空表示不限
   * @param enabled 是否启用过滤，null 表示不限
   */
  public List<OpenAppClientRow> listRows(int offset, int limit, String keyword, Boolean enabled) {
    StringBuilder sql = new StringBuilder("SELECT " + SELECT_ROW_COLUMNS + "FROM openapp_client ");
    List<Object> args = new java.util.ArrayList<>();
    appendWhere(sql, args, keyword, enabled);
    sql.append("ORDER BY id DESC LIMIT ? OFFSET ?");
    args.add(limit);
    args.add(offset);
    return jdbc.query(sql.toString(), ROW_MAPPER, args.toArray());
  }

  /** 计数（与 {@link #listRows} 同条件）。 */
  public long countRows(String keyword, Boolean enabled) {
    StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM openapp_client ");
    List<Object> args = new java.util.ArrayList<>();
    appendWhere(sql, args, keyword, enabled);
    Long count = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
    return count == null ? 0L : count;
  }

  private static void appendWhere(
      StringBuilder sql, List<Object> args, String keyword, Boolean enabled) {
    boolean hasKeyword = keyword != null && !keyword.isBlank();
    if (hasKeyword) {
      sql.append(
          "WHERE (LOWER(client_id) LIKE LOWER(?) OR LOWER(COALESCE(client_name,'')) LIKE LOWER(?)) ");
      String like = "%" + keyword.trim() + "%";
      args.add(like);
      args.add(like);
    }
    if (enabled != null) {
      sql.append(hasKeyword ? "AND " : "WHERE ");
      sql.append("enabled = ? ");
      args.add(enabled);
    }
  }

  /** 按数据库 id 删除客户端。 */
  public int deleteRowById(Long id) {
    return jdbc.update("DELETE FROM openapp_client WHERE id = ?", id);
  }

  /** 更新启用状态（仅此字段；其他字段通过 {@link #update(RegisteredClient)} 管理）。 */
  public int updateEnabled(Long id, boolean enabled) {
    return jdbc.update("UPDATE openapp_client SET enabled = ? WHERE id = ?", enabled, id);
  }

  /** 更新 client_name（OAuth2 SDK 不感知的字段）。 */
  public int updateName(Long id, String clientName) {
    return jdbc.update("UPDATE openapp_client SET client_name = ? WHERE id = ?", clientName, id);
  }

  /**
   * 管理 API 全字段更新（redirect_uris / post_logout_redirect_uris / scopes / grant_types /
   * authentication_methods / enabled / client_name），不修改 client_secret、client_id。
   */
  public int updateManagementFields(
      Long id,
      String clientName,
      Set<String> redirectUris,
      Set<String> postLogoutRedirectUris,
      Set<String> scopes,
      Set<AuthorizationGrantType> grantTypes,
      Set<ClientAuthenticationMethod> authMethods,
      boolean enabled) {
    return jdbc.update(
        "UPDATE openapp_client SET "
            + "client_name = ?, redirect_uris = ?, post_logout_redirect_uris = ?, "
            + "scopes = ?, grant_types = ?, authentication_methods = ?, enabled = ? "
            + "WHERE id = ?",
        clientName,
        toCsv(redirectUris),
        toCsv(postLogoutRedirectUris),
        toCsv(scopes),
        toCsvGrantTypes(grantTypes),
        toCsvAuthMethods(authMethods),
        enabled,
        id);
  }

  private static RegisteredClient mapRow(ResultSet rs) throws SQLException {
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
