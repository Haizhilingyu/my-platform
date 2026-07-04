# C quickstart

Goal: from zero to a published platform message in under five minutes, using
the C SDK.

You will register an external app, build the static library, get a token via
the `client_credentials` grant, and publish an `URGENT` message to one user.
The SDK depends on **libcurl** only. JSON parsing is handled by a bundled
minimal parser, so you do not need jansson or cJSON.

## Prerequisites

- A C99 compiler (gcc, clang, MSVC).
- libcurl headers and library (`libcurl-dev` / `curl` Homebrew formula).
- A running platform backend at `http://localhost:8090` (default dev URL).
- A user id to send to. The default admin is `1`.

## Step 1. Register an external application

Ask the platform admin to create a client for you. They will return:

- `client_id`, for example `demo-client`
- `client_secret`, for example `demo-secret`
- the list of scopes granted, which must include `notify:publish`

For this quickstart you do not need a `redirect_uri`. That is only required
for the authorization-code flow (web apps acting for a user).

## Step 2. Build the SDK

```bash
cd client-sdk-c
make            # builds libplatformclient.a + example
make test       # unit tests (no network)
```

The artifacts you care about:

- `platform_client.h`, the public header
- `libplatformclient.a`, the static library
- `platform_demo`, the demo binary (run it to sanity-check the build)

## Step 3. Link from your project

Point your build at the SDK header and library, then link libcurl:

```makefile
CFLAGS  += -Ipath/to/client-sdk-c
LDFLAGS += -Lpath/to/client-sdk-c -lplatformclient -lcurl
```

Or, in a single gcc invocation:

```bash
gcc -Iclient-sdk-c quickstart.c client-sdk-c/libplatformclient.a -lcurl -o quickstart
```

## Step 4. Set credentials in the environment

Never hard-code the secret. Export it in the shell you will run the binary
from:

```bash
export PLATFORM_ISSUER="http://localhost:8090"
export PLATFORM_CLIENT_ID="<your-client-id>"
export PLATFORM_CLIENT_SECRET="<your-client-secret>"
export PLATFORM_RECIPIENT_ID="1"
```

## Step 5. Write the code

`quickstart.c`:

```c
#include "platform_client.h"

#include <stdio.h>
#include <stdlib.h>

static const char *env_or(const char *key, const char *def) {
  const char *v = getenv(key);
  return (v && v[0]) ? v : def;
}

int main(void) {
  long long recipient_id = strtoll(env_or("PLATFORM_RECIPIENT_ID", "1"), NULL, 10);

  /* 1. Build the client. Reads credentials from env vars. */
  platform_client *client = platform_client_create(
      env_or("PLATFORM_CLIENT_ID", ""),               /* never hard-code secrets */
      env_or("PLATFORM_CLIENT_SECRET", ""),
      env_or("PLATFORM_ISSUER", "http://localhost:8090"));
  if (!client) {
    fprintf(stderr, "failed to create client\n");
    return 1;
  }

  /* 2. Get a token using client_credentials. Scope matches what the admin granted. */
  platform_token *token = platform_client_credentials(client, "notify:publish");
  if (!token) {
    fprintf(stderr, "client_credentials failed: %s\n", platform_client_last_error(client));
    platform_client_free(client);
    return 1;
  }
  printf("Got token, expires in %lds\n", platform_token_expires_in(token));

  /* 3. Publish an URGENT message to one user. */
  platform_token_manager *tm = platform_token_manager_create(client, token, "notify:publish");
  platform_token_free(token);                          /* manager holds its own copy */

  platform_publish_request *req = platform_publish_request_single(
      "Hello from C SDK",
      "Sent via client_credentials + /openapi/notify/publish",
      PLATFORM_LEVEL_URGENT,
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

  if (platform_publish_result_http_status(result) == 200 &&
      platform_publish_result_code(result) == 200) {
    printf("Published: messageId=%lld recipientCount=%d\n",
           (long long)platform_publish_result_message_id(result),
           platform_publish_result_recipient_count(result));
  } else {
    fprintf(stderr, "publish HTTP %ld code=%d: %s\n",
            platform_publish_result_http_status(result),
            platform_publish_result_code(result),
            platform_publish_result_message(result));
  }

  platform_publish_result_free(result);
  platform_publish_request_free(req);
  platform_token_manager_free(tm);
  platform_client_free(client);
  return 0;
}
```

Build and run:

```bash
gcc -Iclient-sdk-c quickstart.c client-sdk-c/libplatformclient.a -lcurl -o quickstart
./quickstart
```

You should see:

```
Got token, expires in 300s
Published: messageId=42 recipientCount=1
```

That is the whole integration. Log in to the platform web UI as user `1` and
the message will be in the inbox. Because the level is `URGENT`, it was also
pushed over WebSocket if the user was online.

## Memory management

The C SDK follows the classic create/free contract. Every `*_create`,
`*_credentials`, `*_single`, `*_publish` return value must be released by the
matching `*_free`. Two exceptions:

- `platform_authorization_url` returns a `char *` that **you** must `free()`.
- `platform_token_manager_current` returns a borrowed pointer owned by the
  manager. Do not free it.

The pattern in the sample above is the safe one: free in reverse order of
creation, and free `token` after handing it to the manager.

## What the SDK does for you

- Sends `POST /oauth2/token` with `grant_type=client_credentials`, client
  credentials in HTTP Basic, and `scope=notify:publish` in the form body.
- Wraps the client and token in a `platform_token_manager` that refreshes
  reactively on `401` (using `refresh_token` if one is present, otherwise
  re-issuing `client_credentials`) and retries the publish exactly once.
- On any unrecoverable error, returns `NULL` and sets
  `platform_client_last_error(client)` to a human-readable message.

For the authorization-code flow (web apps acting for a user), swap step 2 for
`platform_authorization_url(...)` plus `platform_exchange_code(...)`. Full
pattern is in [oauth2-flow.md](oauth2-flow.md#authorization-code-flow) and the
SDK README.

## Going further

- Fan out to a role or unit instead of one user. Build the request field by
  field and add recipients:

  ```c
  platform_publish_request *req = platform_publish_request_create();
  platform_publish_request_set_title(req, "Deploy");
  platform_publish_request_set_content(req, "v1.2 out");
  platform_publish_request_set_level(req, PLATFORM_LEVEL_URGENT);
  platform_publish_request_add_recipient(req, PLATFORM_RECIPIENT_ROLE, 2);
  platform_publish_request_add_recipient(req, PLATFORM_RECIPIENT_UNIT, 10); /* sub-units included */
  ```

- Send a lower-priority message that only writes to the inbox (no push):

  ```c
  platform_publish_request_single("Weekly digest", "Nothing urgent, just FYI",
      PLATFORM_LEVEL_NORMAL, PLATFORM_RECIPIENT_USER, recipient_id);
  ```

- Attach a business type so the UI can group and filter:

  ```c
  platform_publish_request_set_business_type(req, "ci.pipeline");
  ```

- Set an expiry so stale messages disappear from inboxes after a deadline:

  ```c
  platform_publish_request_set_expire_time(req, "2026-12-31T23:59:59");
  ```

## Troubleshooting

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| `client_credentials failed: ... 401` | Wrong `client_id` / `client_secret`, or wrong issuer URL. | Check env vars. Hit `/.well-known/oauth-authorization-server` with curl to confirm the issuer. |
| `client_credentials failed: ... 400 invalid_scope` | The client was not granted `notify:publish`. | Ask the platform admin to add the scope. |
| `publish failed: ... 401` after retry | Token was valid at issue but the client lost the scope, or clock skew. | Re-run. If it persists, the manager already retried once; check the admin did not revoke the client. |
| `publish HTTP 400` with a validation message | Missing `title`, `content`, `level`, or `recipients`. | All four fields are required. See [api-reference.md](api-reference.md#post-openapinotifypublish). |
| `curl: (7) Failed to connect` | Backend is not running, or wrong port. | Default is `http://localhost:8090`. |
| Linker error `undefined reference to curl_*` | libcurl not linked. | Add `-lcurl` after the static lib on the link line. |

## Full example

A ready-to-run demo lives in the SDK source tree:

```bash
cd client-sdk-c
make example
PLATFORM_CLIENT_ID=... PLATFORM_CLIENT_SECRET=... ./platform_demo
```

Source: `client-sdk-c/example.c`.
