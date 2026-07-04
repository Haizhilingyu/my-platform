package com.example.openapp.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.web.client.RestTemplate;

import com.example.openapp.client.JdbcRegisteredClientRepository;

class LogoutWebhookServiceTest {

  private JdbcTemplate jdbc;
  private JdbcRegisteredClientRepository clientRepository;
  private RestTemplate restTemplate;
  private LogoutWebhookService service;

  @BeforeEach
  void setUp() throws Exception {
    DataSource ds =
        new SingleConnectionDataSource(
            "jdbc:h2:mem:webhooks;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE",
            "sa",
            "",
            true);
    jdbc = new JdbcTemplate(ds);
    jdbc.execute("DROP ALL OBJECTS");
    try (Connection con = ds.getConnection()) {
      ScriptUtils.executeSqlScript(
          con, new ClassPathResource("db/migration/V30__openapp_init.sql"));
      ScriptUtils.executeSqlScript(
          con, new ClassPathResource("db/migration/V31__openapp_logout_webhook.sql"));
    }
    clientRepository = new JdbcRegisteredClientRepository(jdbc);
    restTemplate = Mockito.mock(RestTemplate.class);
    service = new LogoutWebhookService(clientRepository, restTemplate);
  }

  @Test
  void fireLogoutWebhooksPostsToClientsWithActiveAuthorizationAndWebhookConfigured() {
    insertClient("app-with-webhook", "http://example.com/logout-hook");
    insertClient("app-no-webhook", null);
    insertClient("app-no-authz", "http://example.com/orphan-hook");
    insertAuthorization("user-1", "app-with-webhook");
    insertAuthorization("user-1", "app-no-webhook");
    stubOk();

    int fired = service.fireLogoutWebhooks("user-1", "alice");

    assertThat(fired).isEqualTo(1);
    verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void webhookPayloadContainsEventUserIdUsernameAndTimestamp() {
    insertClient("app-1", "http://example.com/hook");
    insertAuthorization("user-2", "app-1");
    stubOk();

    service.fireLogoutWebhooks("user-2", "bob");

    ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(restTemplate).postForEntity(anyString(), payloadCaptor.capture(), eq(String.class));
    Map<String, Object> payload = payloadCaptor.getValue();
    assertThat(payload).hasSize(4);
    assertThat(payload.get("event")).isEqualTo("logout");
    assertThat(payload.get("userId")).isEqualTo("user-2");
    assertThat(payload.get("username")).isEqualTo("bob");
    assertThat(payload.get("timestamp")).isInstanceOf(String.class);
  }

  @Test
  void webhookFiresOncePerClientEvenWithMultipleAuthorizationsForSameUser() {
    insertClient("app-dup", "http://example.com/dup");
    insertAuthorization("user-3", "app-dup");
    insertAuthorization("user-3", "app-dup");
    stubOk();

    int fired = service.fireLogoutWebhooks("user-3", "carol");

    assertThat(fired).isEqualTo(1);
    verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
  }

  @Test
  void non2xxResponseDoesNotCountAsSuccessButDoesNotThrow() {
    insertClient("app-fail", "http://example.com/fail");
    insertAuthorization("user-4", "app-fail");
    when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
        .thenReturn(new ResponseEntity<>("error", HttpStatus.INTERNAL_SERVER_ERROR));

    int fired = service.fireLogoutWebhooks("user-4", "dave");

    assertThat(fired).isZero();
  }

  @Test
  void restTemplateExceptionIsCaughtAndDoesNotPropagate() {
    insertClient("app-throws", "http://example.com/throws");
    insertAuthorization("user-5", "app-throws");
    when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
        .thenThrow(new RuntimeException("connection refused"));

    int fired = service.fireLogoutWebhooks("user-5", "eve");

    assertThat(fired).isZero();
  }

  @Test
  void noAuthorizationsReturnsZeroWithoutCallingRestTemplate() {
    insertClient("app-orphan", "http://example.com/orphan");

    int fired = service.fireLogoutWebhooks("ghost-user", "nobody");

    assertThat(fired).isZero();
    verify(restTemplate, times(0)).postForEntity(anyString(), any(), any(Class.class));
  }

  @Test
  void nullUsernameFallsBackToPrincipalName() {
    insertClient("app-nulluser", "http://example.com/null");
    insertAuthorization("user-6", "app-nulluser");
    stubOk();

    service.fireLogoutWebhooks("user-6", null);

    ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(restTemplate).postForEntity(anyString(), payloadCaptor.capture(), eq(String.class));
    assertThat(payloadCaptor.getValue().get("username")).isEqualTo("user-6");
  }

  @Test
  void multipleClientsEachReceiveWebhook() {
    insertClient("app-a", "http://example.com/a");
    insertClient("app-b", "http://example.com/b");
    insertClient("app-c-no-webhook", null);
    insertAuthorization("user-7", "app-a");
    insertAuthorization("user-7", "app-b");
    insertAuthorization("user-7", "app-c-no-webhook");
    stubOk();

    int fired = service.fireLogoutWebhooks("user-7", "frank");

    assertThat(fired).isEqualTo(2);
    verify(restTemplate, times(2)).postForEntity(anyString(), any(), eq(String.class));
  }

  private void stubOk() {
    when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
        .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));
  }

  private void insertClient(String clientId, String webhookUrl) {
    RegisteredClient client =
        RegisteredClient.withId(clientId)
            .clientId(clientId)
            .clientSecret("hash")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://127.0.0.1:8080/callback")
            .postLogoutRedirectUri("http://127.0.0.1:8080/")
            .scope("openid")
            .build();
    clientRepository.insert(client, "Test " + clientId);
    if (webhookUrl != null) {
      jdbc.update(
          "UPDATE openapp_client SET logout_webhook_url = ? WHERE client_id = ?",
          webhookUrl,
          clientId);
    }
  }

  private void insertAuthorization(String principalName, String clientId) {
    jdbc.update(
        "INSERT INTO oauth_authorization "
            + "(registered_client_id, principal_name, access_token, attributes) "
            + "VALUES (?, ?, ?, ?)",
        clientId,
        principalName,
        "fake-access-token",
        "{}");
  }
}
