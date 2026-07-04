import base64

import pytest
import responses

from platform_client import (
    PlatformClient,
    PlatformClientError,
    PublishRequest,
    RecipientType,
    TokenError,
)

ISSUER = "http://localhost:8090"
TOKEN_URL = f"{ISSUER}/oauth2/token"
PUBLISH_URL = f"{ISSUER}/openapi/notify/publish"
AUTH_URL = f"{ISSUER}/oauth2/authorize"


def make_client():
    return PlatformClient(ISSUER, client_id="test-client", client_secret="s3cr3t")


@responses.activate
def test_client_credentials_returns_and_caches_token():
    responses.add(
        responses.POST,
        TOKEN_URL,
        json={
            "access_token": "token-A",
            "token_type": "Bearer",
            "expires_in": 3600,
            "scope": "notify:publish",
        },
        status=200,
    )
    client = make_client()
    token = client.client_credentials("notify:publish")

    assert token.access_token == "token-A"
    assert token.expires_in == 3600
    req = responses.calls[0].request
    assert "grant_type=client_credentials" in req.body
    assert "scope=notify%3Apublish" in req.body
    expected = base64.b64encode(b"test-client:s3cr3t").decode()
    assert req.headers["Authorization"] == f"Basic {expected}"


@responses.activate
def test_publish_message_sends_bearer_and_correct_body():
    responses.add(
        responses.POST,
        TOKEN_URL,
        json={"access_token": "token-A", "expires_in": 3600},
        status=200,
    )
    responses.add(
        responses.POST,
        PUBLISH_URL,
        json={"code": 200, "msg": "ok", "data": {"messageId": 42, "recipientCount": 3}},
        status=200,
    )

    client = make_client()
    client.client_credentials(None)
    request = PublishRequest.urgent("hi", "body").add_recipient(RecipientType.USER, 7)

    resp = client.publish_message(request)

    assert resp.messageId == 42
    assert resp.recipientCount == 3
    publish_req = responses.calls[1].request
    assert publish_req.headers["Authorization"] == "Bearer token-A"
    import json as _json

    body = _json.loads(publish_req.body)
    assert body["title"] == "hi"
    assert body["level"] == "URGENT"
    assert body["recipients"] == [{"type": "USER", "id": 7}]


@responses.activate
def test_publish_with_explicit_token_skips_auth_and_caching():
    responses.add(
        responses.POST,
        PUBLISH_URL,
        json={"code": 200, "msg": "ok", "data": {"messageId": 99, "recipientCount": 1}},
        status=200,
    )
    client = make_client()
    resp = client.publish_message(
        "explicit-token",
        PublishRequest.urgent("t", "c").add_recipient(RecipientType.USER, 1),
    )
    assert resp.messageId == 99
    assert responses.calls[0].request.headers["Authorization"] == "Bearer explicit-token"


@responses.activate
def test_unauthorized_401_triggers_client_credentials_reissue_and_retry():
    responses.add(responses.POST, TOKEN_URL,
                  json={"access_token": "expired", "expires_in": 3600}, status=200)
    responses.add(responses.POST, PUBLISH_URL, json={"error": "invalid_token"}, status=401)
    responses.add(responses.POST, TOKEN_URL,
                  json={"access_token": "refreshed", "expires_in": 3600}, status=200)
    responses.add(
        responses.POST,
        PUBLISH_URL,
        json={"code": 200, "msg": "ok", "data": {"messageId": 5, "recipientCount": 1}},
        status=200,
    )

    client = make_client()
    client.client_credentials(None)
    resp = client.publish_message(
        PublishRequest.urgent("t", "c").add_recipient(RecipientType.USER, 1)
    )

    assert resp.messageId == 5
    token_calls = [c for c in responses.calls if c.request.url == TOKEN_URL]
    publish_calls = [c for c in responses.calls if c.request.url == PUBLISH_URL]
    assert len(token_calls) == 2
    assert len(publish_calls) == 2
    assert publish_calls[0].request.headers["Authorization"] == "Bearer expired"
    assert publish_calls[1].request.headers["Authorization"] == "Bearer refreshed"


@responses.activate
def test_refresh_token_grant_used_when_available():
    responses.add(
        responses.POST,
        TOKEN_URL,
        json={"access_token": "access-1", "refresh_token": "refresh-1", "expires_in": 3600},
        status=200,
    )
    responses.add(
        responses.POST,
        TOKEN_URL,
        json={"access_token": "access-2", "refresh_token": "refresh-2", "expires_in": 3600},
        status=200,
    )

    client = make_client()
    client.exchange_code("code123", "http://callback")
    refreshed = client.refresh_token()

    assert refreshed.access_token == "access-2"
    refresh_req = responses.calls[1].request
    assert "grant_type=refresh_token" in refresh_req.body
    assert "refresh_token=refresh-1" in refresh_req.body


@responses.activate
def test_expired_cached_token_triggers_refresh_before_publish():
    responses.add(responses.POST, TOKEN_URL,
                  json={"access_token": "first", "expires_in": 1}, status=200)
    responses.add(responses.POST, TOKEN_URL,
                  json={"access_token": "second", "expires_in": 3600}, status=200)
    responses.add(
        responses.POST,
        PUBLISH_URL,
        json={"code": 200, "msg": "ok", "data": {"messageId": 8, "recipientCount": 1}},
        status=200,
    )

    client = make_client()
    client.client_credentials(None)
    import time

    time.sleep(1.05)
    assert client._needs_refresh() is True

    resp = client.publish_message(
        PublishRequest.urgent("t", "c").add_recipient(RecipientType.USER, 1)
    )
    assert resp.messageId == 8
    publish_calls = [c for c in responses.calls if c.request.url == PUBLISH_URL]
    assert publish_calls[0].request.headers["Authorization"] == "Bearer second"


@responses.activate
def test_token_endpoint_error_propagates():
    responses.add(responses.POST, TOKEN_URL,
                  json={"error": "invalid_client"}, status=400)
    client = make_client()
    with pytest.raises(TokenError) as ei:
        client.client_credentials("notify:publish")
    assert ei.value.status_code == 400


def test_authorization_url_contains_required_params():
    client = PlatformClient(
        ISSUER, client_id="cid", client_secret="cs", redirect_uri="http://cb/cb"
    )
    url = client.authorization_url(state="xyz", scope="openid notify:publish")
    assert url.startswith(f"{AUTH_URL}?")
    assert "client_id=cid" in url
    assert "redirect_uri=http%3A%2F%2Fcb%2Fcb" in url
    assert "state=xyz" in url
    assert "scope=openid+notify%3Apublish" in url
