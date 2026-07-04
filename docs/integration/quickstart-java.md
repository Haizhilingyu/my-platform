# Java quickstart

Goal: from zero to a published platform message in under five minutes, using
the Java SDK on the JVM.

You will register an external app, drop the SDK into a Maven project, get a
token via the `client_credentials` grant, and publish an `URGENT` message to
one user. Same shape works for any JVM language: Java, Kotlin, Scala, Clojure.

## Prerequisites

- Java 17 or newer.
- Maven 3.6+ (only needed if you build the SDK yourself).
- A running platform backend at `http://localhost:8090` (default dev URL).
- A user id to send to. The default admin is `1`.

## Step 1. Register an external application

Ask the platform admin to create a client for you. They will return:

- `client_id`, for example `demo-client`
- `client_secret`, for example `demo-secret`
- the list of scopes granted, which must include `notify:publish`

For this quickstart you do not need a `redirect_uri`. That is only required
for the authorization-code flow (web apps acting for a user).

## Step 2. Add the SDK to your project

The SDK ships to the platform Nexus at `192.168.1.2:8081`. Add the repository
and the dependency to your `pom.xml`:

```xml
<repositories>
  <repository>
    <id>platform-nexus</id>
    <url>http://192.168.1.2:8081/repository/maven-public/</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.example</groupId>
    <artifactId>client-sdk-java</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </dependency>
</dependencies>
```

No Nexus access? Build it locally:

```bash
cd backend/client-sdk-java
mvn clean install
```

Then the dependency resolves from your local `~/.m2` with the same coordinates.

## Step 3. Set credentials in the environment

Never hard-code the secret. Export it in the shell you will run the app from:

```bash
export PLATFORM_ISSUER="http://localhost:8090"
export PLATFORM_CLIENT_ID="<your-client-id>"
export PLATFORM_CLIENT_SECRET="<your-client-secret>"
export PLATFORM_RECIPIENT_ID="1"
```

## Step 4. Write the code

`src/main/java/com/example/Quickstart.java`:

```java
package com.example;

import com.example.clientsdk.PlatformClient;
import com.example.clientsdk.PublishRequest;
import com.example.clientsdk.PublishResponse;
import com.example.clientsdk.RecipientType;
import com.example.clientsdk.TokenResponse;

public class Quickstart {
  public static void main(String[] args) {
    long recipientId = Long.parseLong(System.getenv().getOrDefault("PLATFORM_RECIPIENT_ID", "1"));

    // 1. Build the client. Reads credentials from env vars.
    PlatformClient client = PlatformClient.builder()
        .issuerUrl(System.getenv().getOrDefault("PLATFORM_ISSUER", "http://localhost:8090"))
        .clientId(System.getenv("PLATFORM_CLIENT_ID"))        // never hard-code secrets
        .clientSecret(System.getenv("PLATFORM_CLIENT_SECRET"))
        .build();

    // 2. Get a token using client_credentials. Scope matches what the admin granted.
    TokenResponse token = client.clientCredentials("notify:publish");
    System.out.printf("Got token, expires in %ds%n", token.getExpiresIn());

    // 3. Publish an URGENT message to one user.
    PublishRequest request = PublishRequest.urgent("Hello from Java SDK", "Sent via client_credentials")
        .addRecipient(RecipientType.USER, recipientId);

    PublishResponse resp = client.publishMessage(request);
    System.out.printf("Published: messageId=%d recipientCount=%d%n",
        resp.getMessageId(), resp.getRecipientCount());
  }
}
```

Compile and run:

```bash
mvn compile exec:java -Dexec.mainClass=com.example.Quickstart
```

You should see:

```
Got token, expires in 300s
Published: messageId=42 recipientCount=1
```

That is the whole integration. Log in to the platform web UI as user `1` and
the message will be in the inbox. Because the level is `URGENT`, it was also
pushed over WebSocket if the user was online.

## What the SDK does for you

The snippet above is deceptively small. The SDK is doing real work:

- Sends `POST /oauth2/token` with `grant_type=client_credentials`, client
  credentials in HTTP Basic, and `scope=notify:publish` in the form body.
- Caches the resulting access token and its expiry.
- On `publishMessage`, refreshes proactively before expiry, and retries once
  on `401` (re-issuing the `client_credentials` grant).
- Throws a `PlatformClientException` with the HTTP status code on any error.

For the authorization-code flow (web apps acting for a user), swap step 2 for
`authorizationUrl(...)` plus `exchangeCode(...)`. Full pattern is in
[oauth2-flow.md](oauth2-flow.md#authorization-code-flow) and the SDK README.

## Going further

- Fan out to a role or unit instead of one user:

  ```java
  PublishRequest.urgent("Deploy", "v1.2 out")
      .addRecipient(RecipientType.ROLE, 2L)   // role id 2
      .addRecipient(RecipientType.UNIT, 10L); // unit id 10 (includes sub-units)
  ```

- Send a lower-priority message that only writes to the inbox (no push):

  ```java
  PublishRequest req = new PublishRequest();
  req.setTitle("Weekly digest");
  req.setContent("Nothing urgent, just FYI");
  req.setLevel(MessageLevel.NORMAL);
  req.addRecipient(RecipientType.USER, recipientId);
  ```

- Attach a business type so the UI can group and filter:

  ```java
  request.setBusinessType("ci.pipeline");
  ```

- Set an expiry so stale messages disappear from inboxes after a deadline:

  ```java
  request.setExpireTime("2026-12-31T23:59:59");
  ```

## Troubleshooting

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| `Token endpoint returned 401` | Wrong `client_id` / `client_secret`, or wrong issuer URL. | Check env vars. Hit `/.well-known/oauth-authorization-server` to confirm the issuer. |
| `Token endpoint returned 400 invalid_scope` | The client was not granted `notify:publish`. | Ask the platform admin to add the scope. |
| `Publish returned 401` | Token was valid at issue but the client lost the scope, or clock skew. | Re-run. If it persists, the SDK already retried once; check the admin did not revoke the client. |
| `Publish returned 400` with a validation message | Missing `title`, `content`, `level`, or `recipients`. | All four fields are required. See [api-reference.md](api-reference.md#post-openapinotifypublish). |
| Connection refused | Backend is not running, or wrong port. | Default is `http://localhost:8090`. |

## Full example

A ready-to-run demo lives in the SDK source tree:

```bash
cd backend/client-sdk-java
mvn compile
mvn exec:java -Dexec.mainClass=com.example.clientsdk.example.Main
```

Source: `backend/client-sdk-java/src/main/java/com/example/clientsdk/example/Main.java`.
