# my-platform Client SDK for C

OAuth2 + message-publish client for the my-platform open API. Depends on
**libcurl** only — JSON parsing is handled by a bundled minimal parser
(`platform_json.c`).

## Build

```bash
cd client-sdk-c
make            # builds libplatformclient.a + example
make test       # builds and runs unit tests (no network)
```

## Targets

| Target | Description |
|---|---|
| `make lib` | Static library `libplatformclient.a` |
| `make example` | `./platform_demo` demo binary |
| `make test` | Unit tests for JSON parser + request builder + URL builder |
| `make clean` | Remove build artifacts |

## Linking from your project

```makefile
CFLAGS += -Ipath/to/client-sdk-c
LDFLAGS += -Lpath/to/client-sdk-c -lplatformclient -lcurl
```

## Quick start (client_credentials)

```c
#include "platform_client.h"

int main(void) {
  platform_client *c = platform_client_create(
      "client-id", "client-secret", "http://localhost:8090");

  platform_token *tok = platform_client_credentials(c, "notify:publish");
  platform_token_manager *tm = platform_token_manager_create(c, tok, "notify:publish");
  platform_token_free(tok);

  platform_publish_request *req = platform_publish_request_single(
      "Title", "Body", PLATFORM_LEVEL_URGENT, PLATFORM_RECIPIENT_USER, 1);

  platform_publish_result *r = platform_token_manager_publish(tm, req);
  printf("messageId=%lld\n", (long long)platform_publish_result_message_id(r));

  platform_publish_result_free(r);
  platform_publish_request_free(req);
  platform_token_manager_free(tm);
  platform_client_free(c);
  return 0;
}
```

## OAuth2 flows

| Function | Grant |
|---|---|
| `platform_client_credentials` | `client_credentials` |
| `platform_authorization_url` | `authorization_code` (build redirect URL) |
| `platform_exchange_code` | `authorization_code` |
| `platform_refresh_token` | `refresh_token` |

## Auto-refresh

`platform_token_manager_publish` retries once on HTTP 401 after refreshing the
token (via `refresh_token`, falling back to `client_credentials`). On refresh
failure it returns `NULL` and `platform_client_last_error` is set.

## Memory management

Caller frees every `*_create`/`*_credentials`/etc. return value with the
matching `*_free`. `platform_authorization_url` returns a `char *` that the
caller must `free()`. `platform_token_manager_current` returns a borrowed
pointer owned by the manager (do not free).
