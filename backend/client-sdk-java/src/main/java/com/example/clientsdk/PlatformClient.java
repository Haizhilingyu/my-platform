package com.example.clientsdk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client for the My Platform open API. Supports the OAuth2 client-credentials, authorization-code
 * and refresh-token grants, plus message publishing to {@code /openapi/notify/publish}.
 *
 * <p>Auto-refresh: a publish call that returns 401 transparently refreshes the cached token (when a
 * refresh_token is available) and retries the request exactly once.
 */
public class PlatformClient {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final String issuerUrl;
  private final String clientId;
  private final String clientSecret;
  private final String redirectUri;
  private final HttpClient httpClient;
  private final TokenStore tokenStore;

  // Remembered so an expired client_credentials token (which has no refresh_token) can be
  // transparently re-issued on 401 / pre-expiry for machine-to-machine callers.
  private String lastClientCredentialsScope;
  private boolean clientCredentialsUsed;

  private PlatformClient(Builder b) {
    this.issuerUrl = stripTrailingSlash(b.issuerUrl);
    this.clientId = b.clientId;
    this.clientSecret = b.clientSecret;
    this.redirectUri = b.redirectUri;
    this.httpClient = b.httpClient != null ? b.httpClient : defaultHttpClient(b);
    this.tokenStore = b.tokenStore != null ? b.tokenStore : new TokenStore();
  }

  private static HttpClient defaultHttpClient(Builder b) {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(b.connectTimeoutMillis))
        .build();
  }

  /** Build the authorization-endpoint URL the end-user should be redirected to. */
  public String authorizationUrl(String state, String scope) {
    StringBuilder sb =
        new StringBuilder(issuerUrl)
            .append("/oauth2/authorize?response_type=code&client_id=")
            .append(urlEncode(clientId));
    if (redirectUri != null) {
      sb.append("&redirect_uri=").append(urlEncode(redirectUri));
    }
    if (scope != null && !scope.isEmpty()) {
      sb.append("&scope=").append(urlEncode(scope));
    }
    if (state != null && !state.isEmpty()) {
      sb.append("&state=").append(urlEncode(state));
    }
    return sb.toString();
  }

  /** Authorization-code grant: exchange the code returned to the redirect_uri for tokens. */
  public TokenResponse exchangeCode(String code, String redirectUriOverride) {
    String redirect = redirectUriOverride != null ? redirectUriOverride : this.redirectUri;
    Map<String, String> form = new LinkedHashMap<>();
    form.put("grant_type", "authorization_code");
    form.put("code", code);
    if (redirect != null) {
      form.put("redirect_uri", redirect);
    }
    TokenResponse token = requestToken(form);
    tokenStore.store(token.getAccessToken(), token.getRefreshToken(), token.getExpiresIn());
    return token;
  }

  /**
   * Client-credentials (machine-to-machine) grant. Caches the resulting token so subsequent {@link
   * #publishMessage(PublishRequest)} calls benefit from auto-refresh-on-401.
   */
  public TokenResponse clientCredentials(String scope) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("grant_type", "client_credentials");
    if (scope != null && !scope.isEmpty()) {
      form.put("scope", scope);
    }
    TokenResponse token = requestToken(form);
    tokenStore.store(token.getAccessToken(), token.getRefreshToken(), token.getExpiresIn());
    this.lastClientCredentialsScope = scope;
    this.clientCredentialsUsed = true;
    return token;
  }
  public TokenResponse refreshToken() {
    return refreshToken(tokenStore.getRefreshToken());
  }

  /** Refresh using an explicit refresh token, updating the cache. */
  public TokenResponse refreshToken(String refreshToken) {
    if (refreshToken == null || refreshToken.isEmpty()) {
      throw new PlatformClientException("No refresh_token available; call exchangeCode first.", 0);
    }
    Map<String, String> form = new LinkedHashMap<>();
    form.put("grant_type", "refresh_token");
    form.put("refresh_token", refreshToken);
    TokenResponse token = requestToken(form);
    tokenStore.store(token.getAccessToken(), token.getRefreshToken(), token.getExpiresIn());
    return token;
  }

  /** Refresh using the cached refresh token, updating the cache. */

  /**
   * Publish using the cached token, refreshing proactively before expiry and reactively on 401.
   * Requires a prior {@link #clientCredentials(String)} or {@link #exchangeCode(String, String)}.
   */
  public PublishResponse publishMessage(PublishRequest request) {
    if (tokenStore.getAccessToken() == null) {
      throw new PlatformClientException(
          "No access token cached. Call clientCredentials()/exchangeCode() first, "
              + "or use publishMessage(String, PublishRequest).",
          0);
    }
    if (tokenStore.needsRefresh()) {
      proactivelyRefresh();
    }
    try {
      return doPublish(tokenStore.getAccessToken(), request);
    } catch (PlatformClientException e) {
      if (e.getStatusCode() == 401) {
        proactivelyRefresh();
        return doPublish(tokenStore.getAccessToken(), request);
      }
      throw e;
    }
  }

  /** Publish using an explicit access token (no caching, no auto-refresh). */
  public PublishResponse publishMessage(String accessToken, PublishRequest request) {
    return doPublish(accessToken, request);
  }

  private void proactivelyRefresh() {
    if (tokenStore.getRefreshToken() != null) {
      refreshToken(tokenStore.getRefreshToken());
      return;
    }
    if (clientCredentialsUsed) {
      clientCredentials(lastClientCredentialsScope);
      return;
    }
    throw new PlatformClientException(
        "Access token expired and no refresh_token is available. Re-authenticate.", 401);
  }

  private TokenResponse requestToken(Map<String, String> form) {
    try {
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(issuerUrl + "/oauth2/token"))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .header("Accept", "application/json")
              .header("Authorization", basicAuth(clientId, clientSecret))
              .POST(HttpRequest.BodyPublishers.ofString(formEncode(form), StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> resp =
          httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return parseTokenResponse(resp);
    } catch (PlatformClientException e) {
      throw e;
    } catch (Exception e) {
      throw new PlatformClientException("Token request failed: " + e.getMessage(), 0, e);
    }
  }

  private TokenResponse parseTokenResponse(HttpResponse<String> resp) {
    String body = resp.body() == null ? "" : resp.body();
    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
      throw new PlatformClientException(
          "Token endpoint returned " + resp.statusCode() + ": " + body, resp.statusCode());
    }
    try {
      return MAPPER.readValue(body, TokenResponse.class);
    } catch (JsonProcessingException e) {
      throw new PlatformClientException("Malformed token response: " + body, 0, e);
    }
  }

  private PublishResponse doPublish(String accessToken, PublishRequest request) {
    String json;
    try {
      json = MAPPER.writeValueAsString(request);
    } catch (JsonProcessingException e) {
      throw new PlatformClientException("Failed to serialize publish request", 0, e);
    }
    try {
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(issuerUrl + "/openapi/notify/publish"))
              .header("Authorization", "Bearer " + accessToken)
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> resp =
          httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return parsePublishResponse(resp);
    } catch (PlatformClientException e) {
      throw e;
    } catch (Exception e) {
      throw new PlatformClientException("Publish request failed: " + e.getMessage(), 0, e);
    }
  }

  private PublishResponse parsePublishResponse(HttpResponse<String> resp) {
    String body = resp.body() == null ? "" : resp.body();
    if (resp.statusCode() == 401) {
      throw new PlatformClientException("Unauthorized (401)", 401);
    }
    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
      throw new PlatformClientException(
          "Publish returned " + resp.statusCode() + ": " + body, resp.statusCode());
    }
    try {
      JsonNode root = MAPPER.readTree(body);
      JsonNode data = root.has("data") ? root.get("data") : root;
      return MAPPER.treeToValue(data, PublishResponse.class);
    } catch (JsonProcessingException e) {
      throw new PlatformClientException("Malformed publish response: " + body, 0, e);
    }
  }

  private static String formEncode(Map<String, String> form) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, String> e : form.entrySet()) {
      if (!first) {
        sb.append('&');
      }
      sb.append(urlEncode(e.getKey())).append('=').append(urlEncode(e.getValue()));
      first = false;
    }
    return sb.toString();
  }

  private static String urlEncode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }

  private static String basicAuth(String user, String pass) {
    String raw = user + ":" + (pass == null ? "" : pass);
    return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private static String stripTrailingSlash(String s) {
    if (s == null) {
      return "";
    }
    return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
  }

  TokenStore tokenStore() {
    return tokenStore;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String issuerUrl = "http://localhost:8090";
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private long connectTimeoutMillis = 10_000L;
    private HttpClient httpClient;
    private TokenStore tokenStore;

    public Builder issuerUrl(String url) {
      this.issuerUrl = url;
      return this;
    }

    public Builder clientId(String id) {
      this.clientId = id;
      return this;
    }

    public Builder clientSecret(String secret) {
      this.clientSecret = secret;
      return this;
    }

    public Builder redirectUri(String uri) {
      this.redirectUri = uri;
      return this;
    }

    public Builder connectTimeoutMillis(long millis) {
      this.connectTimeoutMillis = millis;
      return this;
    }

    /** Test seam: inject a custom HttpClient (e.g. one targeting a mock server). */
    public Builder httpClient(HttpClient httpClient) {
      this.httpClient = httpClient;
      return this;
    }

    public Builder tokenStore(TokenStore tokenStore) {
      this.tokenStore = tokenStore;
      return this;
    }

    public PlatformClient build() {
      if (clientId == null || clientId.isEmpty()) {
        throw new IllegalArgumentException("clientId is required");
      }
      if (clientSecret == null) {
        this.clientSecret = "";
      }
      return new PlatformClient(this);
    }
  }
}
