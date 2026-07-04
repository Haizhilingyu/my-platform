package com.example.clientsdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlatformClientTest {

  private HttpServer server;
  private RecordingHandler handler;
  private PlatformClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    handler = new RecordingHandler();
    server.createContext("/", handler);
    server.start();
    String issuer = "http://127.0.0.1:" + server.getAddress().getPort();
    client =
        PlatformClient.builder()
            .issuerUrl(issuer)
            .clientId("test-client")
            .clientSecret("s3cr3t")
            .build();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void clientCredentialsReturnsAndCachesToken() {
    handler.enqueueToken("token-A", null, 3600L, "notify:publish");

    TokenResponse token = client.clientCredentials("notify:publish");

    assertEquals("token-A", token.getAccessToken());
    RecordedRequest req = handler.requireOneTo("/oauth2/token");
    assertTrue(req.body.contains("grant_type=client_credentials"));
    assertTrue(req.body.contains("scope=notify%3Apublish"));
    String expectedBasic =
        "Basic "
            + Base64.getEncoder()
                .encodeToString("test-client:s3cr3t".getBytes(StandardCharsets.UTF_8));
    assertEquals(expectedBasic, req.header("Authorization"));
  }

  @Test
  void publishMessageSendsBearerAndCorrectBody() throws Exception {
    handler.enqueueToken("token-A", null, 3600L, null);
    client.clientCredentials(null);

    handler.enqueuePublish(200, 42L, 3);
    PublishRequest req =
        PublishRequest.urgent("hi", "body").addRecipient(RecipientType.USER, 7L);

    PublishResponse resp = client.publishMessage(req);

    assertEquals(42L, resp.getMessageId());
    assertEquals(3, resp.getRecipientCount());
    RecordedRequest publishReq = handler.requireOneTo("/openapi/notify/publish");
    assertEquals("Bearer token-A", publishReq.header("Authorization"));
    JsonNode body = mapper.readTree(publishReq.body);
    assertEquals("hi", body.get("title").asText());
    assertEquals("URGENT", body.get("level").asText());
    assertTrue(body.get("recipients").isArray());
    assertEquals("USER", body.get("recipients").get(0).get("type").asText());
    assertEquals(7, body.get("recipients").get(0).get("id").asInt());
  }

  @Test
  void publishWithExplicitTokenSkipsAuth() {
    handler.enqueuePublish(200, 99L, 1);
    PublishResponse resp =
        client.publishMessage(
            "explicit-token", PublishRequest.urgent("t", "c").addRecipient(RecipientType.USER, 1L));
    assertEquals(99L, resp.getMessageId());
    assertEquals("Bearer explicit-token", handler.requireOneTo("/openapi/notify/publish").header("Authorization"));
  }

  @Test
  void unauthorized401TriggersClientCredentialsReissueAndRetry() {
    handler.enqueueToken("expired-token", null, 3600L, null);
    client.clientCredentials(null);

    handler.enqueuePublish(401, 0L, 0);
    handler.enqueueToken("refreshed-token", null, 3600L, null);
    handler.enqueuePublish(200, 5L, 1);

    PublishResponse resp =
        client.publishMessage(
            PublishRequest.urgent("t", "c").addRecipient(RecipientType.USER, 1L));

    assertEquals(5L, resp.getMessageId());
    List<RecordedRequest> publishes = handler.requestsTo("/openapi/notify/publish");
    assertEquals(2, publishes.size());
    assertEquals("Bearer expired-token", publishes.get(0).header("Authorization"));
    assertEquals("Bearer refreshed-token", publishes.get(1).header("Authorization"));
    assertEquals(2, handler.requestsTo("/oauth2/token").size());
  }

  @Test
  void refreshTokenGrantUsedWhenAvailable() {
    handler.enqueueToken("access-1", "refresh-1", 3600L, null);
    client.exchangeCode("code123", "http://callback");

    handler.enqueueToken("access-2", "refresh-2", 3600L, null);
    TokenResponse refreshed = client.refreshToken();

    assertEquals("access-2", refreshed.getAccessToken());
    RecordedRequest refreshReq = handler.requestsTo("/oauth2/token").get(1);
    assertTrue(refreshReq.body.contains("grant_type=refresh_token"));
    assertTrue(refreshReq.body.contains("refresh_token=refresh-1"));
  }

  @Test
  void expiredCachedTokenTriggersRefreshBeforePublish() {
    handler.enqueueToken("first", null, 1L, null);
    client.clientCredentials(null);

    try {
      Thread.sleep(20L);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    assertTrue(client.tokenStore().needsRefresh());

    handler.enqueueToken("second", null, 3600L, null);
    handler.enqueuePublish(200, 8L, 1);
    PublishResponse resp =
        client.publishMessage(
            PublishRequest.urgent("t", "c").addRecipient(RecipientType.USER, 1L));
    assertEquals(8L, resp.getMessageId());
    assertEquals(
        "Bearer second", handler.requireOneTo("/openapi/notify/publish").header("Authorization"));
  }

  @Test
  void tokenEndpointErrorPropagates() {
    handler.enqueueRaw("/oauth2/token", 400, "{\"error\":\"invalid_client\"}");
    PlatformClientException ex =
        assertThrows(
            PlatformClientException.class, () -> client.clientCredentials("notify:publish"));
    assertEquals(400, ex.getStatusCode());
  }

  @Test
  void authorizationUrlContainsRequiredParams() {
    String url =
        PlatformClient.builder()
            .issuerUrl("http://localhost:8090")
            .clientId("cid")
            .clientSecret("cs")
            .redirectUri("http://cb/cb")
            .build()
            .authorizationUrl("xyz", "openid notify:publish");
    assertTrue(url.startsWith("http://localhost:8090/oauth2/authorize?"));
    assertTrue(url.contains("client_id=cid"));
    assertTrue(url.contains("redirect_uri=http%3A%2F%2Fcb%2Fcb"));
    assertTrue(url.contains("state=xyz"));
    assertTrue(url.contains("scope=openid+notify%3Apublish"));
  }

  private static final class RecordedRequest {
    final String path;
    final String method;
    final String body;
    final java.util.Map<String, String> headers;

    RecordedRequest(String path, String method, String body, java.util.Map<String, String> headers) {
      this.path = path;
      this.method = method;
      this.body = body;
      this.headers = headers;
    }

    String header(String name) {
      return headers.getOrDefault(name.toLowerCase(), "");
    }
  }

  private static final class RecordingHandler implements HttpHandler {

    private final List<RecordedRequest> requests = new ArrayList<>();
    private final List<Enqueued> enqueued = new ArrayList<>();
    private final AtomicInteger tokenCalls = new AtomicInteger();

    void enqueueToken(String accessToken, String refreshToken, Long expiresIn, String scope) {
      StringBuilder sb = new StringBuilder("{");
      sb.append("\"access_token\":\"").append(accessToken).append("\"");
      sb.append(",\"token_type\":\"Bearer\"");
      if (refreshToken != null) {
        sb.append(",\"refresh_token\":\"").append(refreshToken).append("\"");
      }
      if (expiresIn != null) {
        sb.append(",\"expires_in\":").append(expiresIn);
      }
      if (scope != null) {
        sb.append(",\"scope\":\"").append(scope).append("\"");
      }
      sb.append("}");
      enqueued.add(new Enqueued("/oauth2/token", 200, sb.toString()));
    }

    void enqueuePublish(int status, long messageId, int recipientCount) {
      String body =
          "{\"code\":200,\"msg\":\"ok\",\"data\":{\"messageId\":"
              + messageId
              + ",\"recipientCount\":"
              + recipientCount
              + "}}";
      enqueued.add(new Enqueued("/openapi/notify/publish", status, body));
    }

    void enqueueRaw(String path, int status, String body) {
      enqueued.add(new Enqueued(path, status, body));
    }

    RecordedRequest requireOneTo(String path) {
      List<RecordedRequest> all = requestsTo(path);
      if (all.isEmpty()) {
        throw new AssertionError("No request recorded to " + path);
      }
      return all.get(all.size() - 1);
    }

    List<RecordedRequest> requestsTo(String path) {
      List<RecordedRequest> out = new ArrayList<>();
      for (RecordedRequest r : requests) {
        if (r.path.equals(path)) {
          out.add(r);
        }
      }
      return out;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      String body = "";
      try (InputStream in = exchange.getRequestBody()) {
        body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
      java.util.Map<String, String> hdrs = new java.util.HashMap<>();
      exchange.getRequestHeaders().forEach((k, v) -> hdrs.put(k.toLowerCase(), v.get(0)));
      requests.add(new RecordedRequest(exchange.getRequestURI().getPath(), exchange.getRequestMethod(), body, hdrs));

      String path = exchange.getRequestURI().getPath();
      Enqueued match = null;
      for (Enqueued e : enqueued) {
        if (e.path.equals(path)) {
          match = e;
          break;
        }
      }
      if (match != null) {
        enqueued.remove(match);
      }
      byte[] resp = match == null ? "{}".getBytes(StandardCharsets.UTF_8) : match.body.getBytes(StandardCharsets.UTF_8);
      int status = match == null ? 404 : match.status;
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, resp.length);
      try (java.io.OutputStream os = exchange.getResponseBody()) {
        os.write(resp);
      }
    }

    private static final class Enqueued {
      final String path;
      final int status;
      final String body;

      Enqueued(String path, int status, String body) {
        this.path = path;
        this.status = status;
        this.body = body;
      }
    }
  }
}
