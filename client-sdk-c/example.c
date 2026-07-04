#include "platform_client.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static const char *env_or(const char *key, const char *def) {
  const char *v = getenv(key);
  return (v && v[0]) ? v : def;
}

static long long env_or_int(const char *key, long long def) {
  const char *v = getenv(key);
  if (!v || !v[0]) return def;
  return strtoll(v, NULL, 10);
}

int main(void) {
  const char *client_id = env_or("PLATFORM_CLIENT_ID", "demo-client");
  const char *client_secret = env_or("PLATFORM_CLIENT_SECRET", "demo-secret");
  const char *issuer = env_or("PLATFORM_ISSUER", "http://localhost:8090");
  long long recipient_id = env_or_int("PLATFORM_RECIPIENT_ID", 1);

  platform_client *client = platform_client_create(client_id, client_secret, issuer);
  if (!client) {
    fprintf(stderr, "failed to create client\n");
    return 1;
  }

  platform_token *token = platform_client_credentials(client, "notify:publish");
  if (!token) {
    fprintf(stderr, "client_credentials failed: %s\n", platform_client_last_error(client));
    platform_client_free(client);
    return 1;
  }
  printf("obtained access token (type=%s, expires_in=%ld)\n",
         platform_token_token_type(token),
         platform_token_expires_in(token));

  platform_token_manager *tm = platform_token_manager_create(client, token, "notify:publish");
  platform_token_free(token);

  platform_publish_request *req = platform_publish_request_single(
      "Hello from C SDK",
      "Sent via client_credentials + /openapi/notify/publish",
      PLATFORM_LEVEL_IMPORTANT,
      PLATFORM_RECIPIENT_USER,
      recipient_id);

  platform_publish_result *result = platform_token_manager_publish(tm, req);
  if (!result) {
    fprintf(stderr, "publish failed: %s\n", platform_client_last_error(client));
    platform_publish_request_free(req);
    platform_token_manager_free(tm);
    platform_client_free(client);
    return 1;
  }
  if (platform_publish_result_http_status(result) == 200 && platform_publish_result_code(result) == 200) {
    printf("published: messageId=%lld recipientCount=%d\n",
           (long long)platform_publish_result_message_id(result),
           platform_publish_result_recipient_count(result));
  } else {
    fprintf(stderr, "publish HTTP %ld code %d: %s\n",
            platform_publish_result_http_status(result),
            platform_publish_result_code(result),
            platform_publish_result_raw_body(result));
  }

  platform_publish_result_free(result);
  platform_publish_request_free(req);
  platform_token_manager_free(tm);
  platform_client_free(client);
  return 0;
}
