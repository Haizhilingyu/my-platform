package com.example.clientsdk.example;

import com.example.clientsdk.MessageLevel;
import com.example.clientsdk.PlatformClient;
import com.example.clientsdk.PublishRequest;
import com.example.clientsdk.PublishResponse;
import com.example.clientsdk.RecipientType;
import com.example.clientsdk.TokenResponse;

/**
 * Demo: client_credentials grant -> publish an URGENT message. Run with:
 *
 * <pre>{@code
 * java -cp client-sdk-java-1.0.0-SNAPSHOT.jar:jackson-*.jar \
 *   com.example.clientsdk.example.Main \
 *   http://localhost:8090 <clientId> <clientSecret> <recipientUserId>
 * }</pre>
 *
 * Never hard-code secrets in source: pass them as arguments / env vars.
 */
public class Main {

  public static void main(String[] args) {
    String issuer = args.length > 0 ? args[0] : "http://localhost:8090";
    String clientId = args.length > 1 ? args[1] : envOrThrow("PLATFORM_CLIENT_ID");
    String clientSecret = args.length > 2 ? args[2] : envOrThrow("PLATFORM_CLIENT_SECRET");
    long recipientUserId = args.length > 3 ? Long.parseLong(args[3]) : 1L;

    PlatformClient client =
        PlatformClient.builder()
            .issuerUrl(issuer)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .build();

    TokenResponse token = client.clientCredentials("notify:publish");
    System.out.println("Got access token, expires_in=" + token.getExpiresIn() + "s");

    PublishRequest request =
        PublishRequest.urgent("SDK demo", "Hello from client-sdk-java")
            .addRecipient(RecipientType.USER, recipientUserId);
    PublishResponse result = client.publishMessage(request);
    System.out.println(
        "Published URGENT message id=" + result.getMessageId()
            + " recipientCount=" + result.getRecipientCount());
  }

  private static String envOrThrow(String name) {
    String v = System.getenv(name);
    if (v == null || v.isEmpty()) {
      throw new IllegalArgumentException("Missing arg/env: " + name);
    }
    return v;
  }

  private Main() {}
}
