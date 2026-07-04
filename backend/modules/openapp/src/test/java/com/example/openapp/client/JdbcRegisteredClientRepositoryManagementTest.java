package com.example.openapp.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

@DisplayName("JdbcRegisteredClientRepository 管理 API 方法")
class JdbcRegisteredClientRepositoryManagementTest {

  private JdbcRegisteredClientRepository repository;
  private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() throws Exception {
    DataSource ds =
        new SingleConnectionDataSource(
            "jdbc:h2:mem:clients-mgmt;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE",
            "sa",
            "",
            true);
    JdbcTemplate template = new JdbcTemplate(ds);
    template.execute("DROP ALL OBJECTS");
    try (Connection con = ds.getConnection()) {
      ScriptUtils.executeSqlScript(
          con,
          new org.springframework.core.io.ClassPathResource("db/migration/V30__openapp_init.sql"));
      ScriptUtils.executeSqlScript(
          con,
          new org.springframework.core.io.ClassPathResource(
              "db/migration/V31__openapp_logout_webhook.sql"));
    }
    repository = new JdbcRegisteredClientRepository(template);
    this.jdbc = template;
  }

  private RegisteredClient buildClient(String clientId) {
    return RegisteredClient.withId(clientId)
        .clientId(clientId)
        .clientSecret("hash")
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .redirectUri("http://cb")
        .scope("openid")
        .clientSettings(ClientSettings.builder().build())
        .tokenSettings(TokenSettings.builder().build())
        .build();
  }

  @Test
  @DisplayName("insert + findRowByClientId：返回完整管理行（含 id/name/enabled/createdAt）")
  void insertAndFindRowByClientId_returnsAllFields() {
    repository.insert(buildClient("app-1"), "My App");

    JdbcRegisteredClientRepository.OpenAppClientRow row = repository.findRowByClientId("app-1");

    assertThat(row).isNotNull();
    assertThat(row.id()).isPositive();
    assertThat(row.clientId()).isEqualTo("app-1");
    assertThat(row.clientName()).isEqualTo("My App");
    assertThat(row.redirectUris()).containsExactly("http://cb");
    assertThat(row.scopes()).containsExactly("openid");
    assertThat(row.grantTypes()).containsExactlyInAnyOrder("authorization_code", "refresh_token");
    assertThat(row.enabled()).isTrue();
    assertThat(row.createdAt()).isNotNull();
  }

  @Test
  @DisplayName("findRowById：按 BIGSERIAL id 查询")
  void findRowById_returnsByNumericId() {
    repository.insert(buildClient("app-2"), "Two");
    Long id = repository.findRowByClientId("app-2").id();

    JdbcRegisteredClientRepository.OpenAppClientRow row = repository.findRowById(id);

    assertThat(row).isNotNull();
    assertThat(row.clientId()).isEqualTo("app-2");
  }

  @Test
  @DisplayName("findRowById：未找到 → null")
  void findRowById_missing_returnsNull() {
    assertThat(repository.findRowById(99999L)).isNull();
  }

  @Test
  @DisplayName("listRows：按 id 倒序，分页正确")
  void listRows_orderedDescAndPaginated() {
    for (int i = 1; i <= 5; i++) {
      repository.insert(buildClient("app-" + i), "App " + i);
    }

    var page1 = repository.listRows(0, 2, null, null);
    var page2 = repository.listRows(2, 2, null, null);

    assertThat(page1).hasSize(2);
    assertThat(page1.get(0).id()).isGreaterThan(page1.get(1).id());
    assertThat(page2).hasSize(2);
    assertThat(page1.get(0).id()).isGreaterThan(page2.get(0).id());
    assertThat(repository.countRows(null, null)).isEqualTo(5L);
  }

  @Test
  @DisplayName("listRows：keyword 匹配 client_id")
  void listRows_keywordMatchesClientId() {
    repository.insert(buildClient("app-foo"), "Foo");
    repository.insert(buildClient("app-bar"), "Bar");

    var rows = repository.listRows(0, 50, "FOO", null);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).clientId()).isEqualTo("app-foo");
    assertThat(repository.countRows("FOO", null)).isEqualTo(1L);
  }

  @Test
  @DisplayName("listRows：keyword 匹配 client_name")
  void listRows_keywordMatchesClientName() {
    repository.insert(buildClient("app-1"), "Acme Corp");
    repository.insert(buildClient("app-2"), "Beta Inc");

    var rows = repository.listRows(0, 50, "acme", null);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).clientName()).isEqualTo("Acme Corp");
  }

  @Test
  @DisplayName("listRows：enabled 过滤")
  void listRows_enabledFilter() {
    repository.insert(buildClient("app-on"), "On");
    repository.insert(buildClient("app-off"), "Off");
    jdbc.update("UPDATE openapp_client SET enabled = FALSE WHERE client_id = 'app-off'");

    var enabledRows = repository.listRows(0, 50, null, true);
    var disabledRows = repository.listRows(0, 50, null, false);

    assertThat(enabledRows).extracting(r -> r.clientId()).containsExactly("app-on");
    assertThat(disabledRows).extracting(r -> r.clientId()).containsExactly("app-off");
    assertThat(repository.countRows(null, true)).isEqualTo(1L);
    assertThat(repository.countRows(null, false)).isEqualTo(1L);
  }

  @Test
  @DisplayName("updateManagementFields：替换非 secret/clientId 字段")
  void updateManagementFields_replacesFields() {
    repository.insert(buildClient("app-3"), "Old");
    Long id = repository.findRowByClientId("app-3").id();

    repository.updateManagementFields(
        id,
        "New Name",
        Set.of("http://new1", "http://new2"),
        Set.of("http://logout"),
        Set.of("openid", "notify:publish"),
        Set.of(AuthorizationGrantType.CLIENT_CREDENTIALS),
        Set.of(ClientAuthenticationMethod.CLIENT_SECRET_BASIC),
        false);

    JdbcRegisteredClientRepository.OpenAppClientRow row = repository.findRowById(id);
    assertThat(row.clientName()).isEqualTo("New Name");
    assertThat(row.redirectUris()).containsExactlyInAnyOrder("http://new1", "http://new2");
    assertThat(row.scopes()).containsExactlyInAnyOrder("openid", "notify:publish");
    assertThat(row.grantTypes()).containsExactly("client_credentials");
    assertThat(row.enabled()).isFalse();
    assertThat(row.clientId()).isEqualTo("app-3");
    assertThat(repository.findByClientId("app-3").getClientSecret()).isEqualTo("hash");
  }

  @Test
  @DisplayName("deleteRowById：删除行，返回 1；不存在返回 0")
  void deleteRowById_deletes() {
    repository.insert(buildClient("app-4"), "Four");
    Long id = repository.findRowByClientId("app-4").id();

    assertThat(repository.deleteRowById(id)).isEqualTo(1);
    assertThat(repository.findRowById(id)).isNull();
    assertThat(repository.deleteRowById(id)).isEqualTo(0);
  }

  @Test
  @DisplayName("updateEnabled：单字段更新")
  void updateEnabled_changesFlag() {
    repository.insert(buildClient("app-5"), "Five");
    Long id = repository.findRowByClientId("app-5").id();

    assertThat(repository.updateEnabled(id, false)).isEqualTo(1);
    assertThat(repository.findRowById(id).enabled()).isFalse();
  }
}
