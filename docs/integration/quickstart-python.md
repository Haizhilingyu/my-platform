# Python quickstart

Goal: from zero to a published platform message in under five minutes, using
the Python SDK.

You will register an external app, install the SDK, get a token via the
`client_credentials` grant, and publish an `URGENT` message to one user.
Works on CPython 3.8 and newer.

## Prerequisites

- Python 3.8 or newer.
- A running platform backend at `http://localhost:8090` (default dev URL).
- A user id to send to. The default admin is `1`.

## Step 1. Register an external application

Ask the platform admin to create a client for you. They will return:

- `client_id`, for example `demo-client`
- `client_secret`, for example `demo-secret`
- the list of scopes granted, which must include `notify:publish`

For this quickstart you do not need a `redirect_uri`. That is only required
for the authorization-code flow (web apps acting for a user).

## Step 2. Install the SDK

The SDK ships to the platform Nexus PyPI at `<NAS_IP>:8081`. Point pip at
it and install:

```bash
# One-time: add the private index to ~/.pip/pip.conf
cat >> ~/.pip/pip.conf <<'INI'
[global]
index-url = http://<NAS_IP>:8081/repository/pypi-hosted/simple
extra-index-url = https://pypi.org/simple
trusted-host = <NAS_IP>
INI

pip install my-platform-client
```

No Nexus access? Install from source:

```bash
cd client-sdk-python
pip install -r requirements.txt
python setup.py install
```

The only runtime dependency is `requests>=2.28`.

## Step 3. Set credentials in the environment

Never hard-code the secret. Export it in the shell you will run the script
from:

```bash
export PLATFORM_ISSUER="http://localhost:8090"
export PLATFORM_CLIENT_ID="<your-client-id>"
export PLATFORM_CLIENT_SECRET="<your-client-secret>"
export PLATFORM_RECIPIENT_ID="1"
```

## Step 4. Write the code

`quickstart.py`:

```python
import os
from platform_client import (
    PlatformClient,
    PublishRequest,
    RecipientType,
)

recipient_id = int(os.environ.get("PLATFORM_RECIPIENT_ID", "1"))

# 1. Build the client. Reads credentials from env vars.
client = PlatformClient(
    os.environ.get("PLATFORM_ISSUER", "http://localhost:8090"),
    client_id=os.environ["PLATFORM_CLIENT_ID"],        # never hard-code secrets
    client_secret=os.environ["PLATFORM_CLIENT_SECRET"],
)

# 2. Get a token using client_credentials. Scope matches what the admin granted.
token = client.client_credentials("notify:publish")
print(f"Got token, expires in {token.expires_in}s")

# 3. Publish an URGENT message to one user.
request = (
    PublishRequest.urgent("Hello from Python SDK", "Sent via client_credentials")
    .add_recipient(RecipientType.USER, recipient_id)
)
result = client.publish_message(request)
print(f"Published: messageId={result.messageId} recipientCount={result.recipientCount}")
```

Run it:

```bash
python quickstart.py
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
- On `publish_message`, refreshes proactively before expiry, and retries once
  on `401` (re-issuing the `client_credentials` grant).
- Raises a `PlatformClientError` with the HTTP status code on any error.

For the authorization-code flow (web apps acting for a user), swap step 2 for
`authorization_url(...)` plus `exchange_code(...)`. Full pattern is in
[oauth2-flow.md](oauth2-flow.md#authorization-code-flow) and the SDK README.

## Going further

- Fan out to a role or unit instead of one user:

  ```python
  PublishRequest.urgent("Deploy", "v1.2 out") \
      .add_recipient(RecipientType.ROLE, 2)   \
      .add_recipient(RecipientType.UNIT, 10)  # includes sub-units
  ```

- Send a lower-priority message that only writes to the inbox (no push):

  ```python
  request = PublishRequest(
      title="Weekly digest",
      content="Nothing urgent, just FYI",
      level=MessageLevel.NORMAL,
  )
  request.add_recipient(RecipientType.USER, recipient_id)
  ```

- Attach a business type so the UI can group and filter:

  ```python
  request.business_type = "ci.pipeline"
  ```

- Set an expiry so stale messages disappear from inboxes after a deadline:

  ```python
  request.expire_time = "2026-12-31T23:59:59"
  ```

## Troubleshooting

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| `PlatformClientError: 401` from token endpoint | Wrong `client_id` / `client_secret`, or wrong issuer URL. | Check env vars. Hit `/.well-known/oauth-authorization-server` to confirm the issuer. |
| `PlatformClientError: 400 invalid_scope` | The client was not granted `notify:publish`. | Ask the platform admin to add the scope. |
| `PlatformClientError: 401` from publish | Token was valid at issue but the client lost the scope, or clock skew. | Re-run. If it persists, the SDK already retried once; check the admin did not revoke the client. |
| `PlatformClientError: 400` with a validation message | Missing `title`, `content`, `level`, or `recipients`. | All four fields are required. See [api-reference.md](api-reference.md#post-openapinotifypublish). |
| `requests.exceptions.ConnectionError` | Backend is not running, or wrong port. | Default is `http://localhost:8090`. |

## Full example

A ready-to-run demo lives in the SDK source tree:

```bash
cd client-sdk-python
python example.py http://localhost:8090 "$PLATFORM_CLIENT_ID" "$PLATFORM_CLIENT_SECRET" 1
```

Source: `client-sdk-python/example.py`.
