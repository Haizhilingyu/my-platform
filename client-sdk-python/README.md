# My Platform Client SDK (Python)

OAuth2 client + message-publish SDK for external Python applications integrating with **My Platform**.

## Requirements

- Python 3.8+
- Runtime dependency: `requests>=2.28`

## Install

### From the platform Nexus PyPI

Configure pip to use the private repository (e.g. `~/.pip/pip.conf`):

```ini
[global]
index-url = http://192.168.1.2:8081/repository/pypi-hosted/simple
extra-index-url = https://pypi.org/simple
trusted-host = 192.168.1.2
```

Then:

```bash
pip install my-platform-client
```

### From source

```bash
cd client-sdk-python
pip install -r requirements.txt
python setup.py install
```

## Usage

### Machine-to-machine (client_credentials) → publish URGENT

```python
import os
from platform_client import PlatformClient, PublishRequest, RecipientType

client = PlatformClient(
    "http://localhost:8090",
    client_id=os.environ["PLATFORM_CLIENT_ID"],       # never hard-code secrets
    client_secret=os.environ["PLATFORM_CLIENT_SECRET"],
)
client.client_credentials("notify:publish")

resp = client.publish_message(
    PublishRequest.urgent("Build failed", "Pipeline #42 broke")
    .add_recipient(RecipientType.ROLE, 2)
)
print("messageId=", resp.messageId)
```

The cached token is auto-refreshed: proactively before expiry, and reactively on `401`
(re-issuing the `client_credentials` grant, or using `refresh_token` when one is
available from the authorization-code flow).

### Authorization-code flow (web apps)

```python
client = PlatformClient(
    "http://localhost:8090",
    client_id="web-app",
    client_secret="...",
    redirect_uri="https://app.example.com/callback",
)

# 1. redirect the user's browser to:
url = client.authorization_url(state=csrf_token, scope="openid notify:publish")

# 2. on the callback, exchange the code:
token = client.exchange_code(code)
```

## API reference

| Method | Grant / endpoint |
| --- | --- |
| `authorization_url(state, scope)` | `GET /oauth2/authorize` URL builder |
| `exchange_code(code, redirect_uri=None)` | `POST /oauth2/token` (`authorization_code`) |
| `client_credentials(scope=None)` | `POST /oauth2/token` (`client_credentials`) |
| `refresh_token(refresh_token=None)` | `POST /oauth2/token` (`refresh_token`) |
| `publish_message(request)` | `POST /openapi/notify/publish` (cached token, auto-refresh) |
| `publish_message(token, request)` | `POST /openapi/notify/publish` (explicit token) |

## Publish request shape

```json
{
  "title": "string",
  "content": "string",
  "level": "URGENT | IMPORTANT | NORMAL",
  "businessType": "optional",
  "expireTime": "optional ISO-8601",
  "recipients": [
    {"type": "USER | ROLE | UNIT", "id": 1}
  ]
}
```

`URGENT` triggers an immediate WebSocket push to resolved recipients.

## Tests

```bash
pip install -r requirements-dev.txt
pytest
```

## Publish to the platform Nexus PyPI

```bash
python setup.py sdist bdist_wheel
twine upload \
  --repository-url http://192.168.1.2:8081/repository/pypi-hosted/ \
  -u "$NEXUS_USER" -p "$NEXUS_PASS" \
  dist/*
```
