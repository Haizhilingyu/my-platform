# My Platform Client SDK (Java)

OAuth2 client + message-publish SDK for external Java applications integrating with **My Platform**.

## Requirements

- Java 17+
- Maven 3.6+ (build only)

Runtime dependencies: only `jackson-databind` (HTTP uses the built-in `java.net.http.HttpClient`).

## Install

### From Nexus (private)

Add to your `pom.xml` (the platform Nexus at `<NAS_IP>:8081`):

```xml
<dependencies>
  <dependency>
    <groupId>com.example</groupId>
    <artifactId>client-sdk-java</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </dependency>
</dependencies>
```

### Build locally

```bash
cd backend/client-sdk-java
mvn clean install
```

## Usage

### Machine-to-machine (client_credentials) → publish URGENT

```java
PlatformClient client = PlatformClient.builder()
    .issuerUrl("http://localhost:8090")
    .clientId(System.getenv("PLATFORM_CLIENT_ID"))     // never hard-code secrets
    .clientSecret(System.getenv("PLATFORM_CLIENT_SECRET"))
    .build();

client.clientCredentials("notify:publish");

PublishResponse resp = client.publishMessage(
    PublishRequest.urgent("Build failed", "Pipeline #42 broke")
        .addRecipient(RecipientType.ROLE, 2L));
System.out.println("messageId=" + resp.getMessageId());
```

The cached token is auto-refreshed: proactively before expiry, and reactively on `401` (re-issuing the `client_credentials` grant, or using `refresh_token` when one is available from the authorization-code flow).

### Authorization-code flow (web apps)

```java
PlatformClient client = PlatformClient.builder()
    .issuerUrl("http://localhost:8090")
    .clientId("web-app")
    .clientSecret("...")
    .redirectUri("https://app.example.com/callback")
    .build();

// 1. redirect the user's browser to:
String url = client.authorizationUrl(csrfToken, "openid notify:publish");

// 2. on the callback, exchange the code:
TokenResponse token = client.exchangeCode(code, null);
```

## API reference

| Method | Grant / endpoint |
| --- | --- |
| `authorizationUrl(state, scope)` | `GET /oauth2/authorize` URL builder |
| `exchangeCode(code, redirectUriOverride)` | `POST /oauth2/token` (`authorization_code`) |
| `clientCredentials(scope)` | `POST /oauth2/token` (`client_credentials`) |
| `refreshToken()` / `refreshToken(rt)` | `POST /oauth2/token` (`refresh_token`) |
| `publishMessage(request)` | `POST /openapi/notify/publish` (cached token, auto-refresh) |
| `publishMessage(accessToken, request)` | `POST /openapi/notify/publish` (explicit token) |

## Publish request shape

```json
{
  "title": "string",
  "content": "string",
  "level": "URGENT | IMPORTANT | NORMAL",
  "businessType": "optional",
  "expireTime": "optional ISO-8601",
  "recipients": [
    { "type": "USER | ROLE | UNIT", "id": 1 }
  ]
}
```

`URGENT` triggers an immediate WebSocket push to resolved recipients.

## Tests

```bash
mvn test
```

8 JUnit5 tests run against an in-process JDK `HttpServer` mock (no WireMock needed).

## Publish to Nexus

```bash
mvn deploy
```

`distributionManagement` in `pom.xml` points to the platform Nexus (`<NAS_IP>:8081`).
