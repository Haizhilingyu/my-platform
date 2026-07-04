package com.example.openapp.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

class JdbcRegisteredClientRepositoryTest {

  private JdbcRegisteredClientRepository repository;
  private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() throws Exception {
    DataSource ds =
        new SingleConnectionDataSource(
            "jdbc:h2:mem:clients;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE",
            "sa",
            "",
            true);
    JdbcTemplate jdbc = new JdbcTemplate(ds);
    jdbc.execute("DROP ALL OBJECTS");
    try (Connection con = ds.getConnection()) {
      ScriptUtils.executeSqlScript(
          con,
          new org.springframework.core.io.ClassPathResource("db/migration/V30__openapp_init.sql"));
      ScriptUtils.executeSqlScript(
          con,
          new org.springframework.core.io.ClassPathResource(
              "db/migration/V31__openapp_logout_webhook.sql"));
    }
    repository = new JdbcRegisteredClientRepository(jdbc);
    this.jdbc = jdbc;
  }

  private RegisteredClient buildClient(String clientId, String secretHash) {
    return RegisteredClient.withId(clientId)
        .clientId(clientId)
        .clientSecret(secretHash)
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .redirectUri("http://127.0.0.1:8080/callback")
        .postLogoutRedirectUri("http://127.0.0.1:8080/")
        .scope("openid")
        .scope("openapi.read")
        .clientSettings(ClientSettings.builder().build())
        .tokenSettings(TokenSettings.builder().build())
        .build();
  }

  @Test
  void insertThenFindByClientIdRoundTripsAllFields() {
    RegisteredClient original = buildClient("app-1", "bcrypt-hash");
    repository.insert(original, "My App");

    RegisteredClient loaded = repository.findByClientId("app-1");

    assertThat(loaded).isNotNull();
    assertThat(loaded.getClientId()).isEqualTo("app-1");
    assertThat(loaded.getClientSecret()).isEqualTo("bcrypt-hash");
    assertThat(loaded.getRedirectUris())
        .containsExactlyInAnyOrderElementsOf(Set.of("http://127.0.0.1:8080/callback"));
    assertThat(loaded.getPostLogoutRedirectUris())
        .containsExactlyInAnyOrderElementsOf(Set.of("http://127.0.0.1:8080/"));
    assertThat(loaded.getScopes()).containsExactlyInAnyOrder("openid", "openapi.read");
    assertThat(loaded.getAuthorizationGrantTypes())
        .containsExactlyInAnyOrder(
            AuthorizationGrantType.AUTHORIZATION_CODE,
            AuthorizationGrantType.REFRESH_TOKEN,
            AuthorizationGrantType.CLIENT_CREDENTIALS);
    assertThat(loaded.getClientAuthenticationMethods())
        .contains(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
    assertThat(repository.findNameByClientId("app-1")).isEqualTo("My App");
  }

  @Test
  void findByIdReturnsNullWhenMissing() {
    assertThat(repository.findById("does-not-exist")).isNull();
    assertThat(repository.findByClientId("ghost")).isNull();
  }

  @Test
  void saveInsertsWhenAbsent() {
    RegisteredClient client = buildClient("app-2", "hash");
    repository.save(client);
    assertThat(repository.findByClientId("app-2")).isNotNull();
  }

  @Test
  void saveUpdatesWhenPresentWithoutTouchingSecretOrName() {
    RegisteredClient original = buildClient("app-3", "original-hash");
    repository.insert(original, "Original Name");

    RegisteredClient updated = RegisteredClient.from(original).scope("openapi.write").build();
    repository.save(updated);

    RegisteredClient loaded = repository.findByClientId("app-3");
    assertThat(loaded.getScopes()).contains("openapi.write");
    assertThat(loaded.getClientSecret()).isEqualTo("original-hash");
    assertThat(repository.findNameByClientId("app-3")).isEqualTo("Original Name");
  }

  @Test
  void updateSecretChangesHash() {
    repository.insert(buildClient("app-4", "old"), "N");
    repository.updateSecret("app-4", "new-hash");
    assertThat(repository.findByClientId("app-4").getClientSecret()).isEqualTo("new-hash");
  }

  @Test
  void deleteByClientIdRemovesRow() {
    repository.insert(buildClient("app-5", "h"), "N");
    repository.deleteByClientId("app-5");
    assertThat(repository.findByClientId("app-5")).isNull();
  }

  @Test
  void findActiveLogoutWebhooksReturnsClientsWithWebhookAndActiveAuthorization() {
    repository.insert(buildClient("hook-client", "hash"), "Hook App");
    jdbc.update(
        "UPDATE openapp_client SET logout_webhook_url = ? WHERE client_id = ?",
        "http://example.com/hook",
        "hook-client");
    jdbc.update(
        "INSERT INTO oauth_authorization (registered_client_id, principal_name, access_token, attributes) "
            + "VALUES (?, ?, ?, ?)",
        "hook-client",
        "user-x",
        "token",
        "{}");

    var webhooks = repository.findActiveLogoutWebhooks("user-x");

    assertThat(webhooks).hasSize(1);
    assertThat(webhooks.get(0).clientId()).isEqualTo("hook-client");
    assertThat(webhooks.get(0).webhookUrl()).isEqualTo("http://example.com/hook");
  }

  @Test
  void findActiveLogoutWebhooksExcludesClientsWithoutWebhookUrl() {
    repository.insert(buildClient("no-hook", "hash"), "No Hook");
    jdbc.update(
        "INSERT INTO oauth_authorization (registered_client_id, principal_name, access_token, attributes) "
            + "VALUES (?, ?, ?, ?)",
        "no-hook",
        "user-y",
        "token",
        "{}");

    var webhooks = repository.findActiveLogoutWebhooks("user-y");

    assertThat(webhooks).isEmpty();
  }

  @Test
  void findActiveLogoutWebhooksExcludesClientsWithoutAuthorization() {
    repository.insert(buildClient("orphan-hook", "hash"), "Orphan");
    jdbc.update(
        "UPDATE openapp_client SET logout_webhook_url = ? WHERE client_id = ?",
        "http://example.com/orphan",
        "orphan-hook");

    var webhooks = repository.findActiveLogoutWebhooks("user-z");

    assertThat(webhooks).isEmpty();
  }

  @Test
  void findActiveLogoutWebhooksDeduplicatesByClientId() {
    repository.insert(buildClient("dup-client", "hash"), "Dup");
    jdbc.update(
        "UPDATE openapp_client SET logout_webhook_url = ? WHERE client_id = ?",
        "http://example.com/dup",
        "dup-client");
    jdbc.update(
        "INSERT INTO oauth_authorization (registered_client_id, principal_name, access_token, attributes) "
            + "VALUES (?, ?, ?, ?)",
        "dup-client",
        "user-w",
        "token1",
        "{}");
    jdbc.update(
        "INSERT INTO oauth_authorization (registered_client_id, principal_name, access_token, attributes) "
            + "VALUES (?, ?, ?, ?)",
        "dup-client",
        "user-w",
        "token2",
        "{}");

    var webhooks = repository.findActiveLogoutWebhooks("user-w");

    assertThat(webhooks).hasSize(1);
  }
}
