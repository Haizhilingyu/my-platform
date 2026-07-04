"""PlatformClient: OAuth2 grants + message publish with token auto-refresh."""
from __future__ import annotations

import time
from typing import Optional
from urllib.parse import urlencode

import requests

from platform_client.exceptions import PlatformClientError, TokenError
from platform_client.models import (
    MessageLevel,
    PublishRequest,
    PublishResponse,
    RecipientType,
    TokenResponse,
)

_DEFAULT_ISSUER = "http://localhost:8090"
_TOKEN_ENDPOINT = "/oauth2/token"
_AUTHORIZE_ENDPOINT = "/oauth2/authorize"
_PUBLISH_ENDPOINT = "/openapi/notify/publish"
_CLOCK_SKEW_SECONDS = 10


class PlatformClient:
    """Client for the My Platform open API.

    Supports the OAuth2 client-credentials, authorization-code and refresh-token
    grants, plus message publishing to ``/openapi/notify/publish``.

    Auto-refresh: a publish call that returns 401 transparently refreshes the
    cached token (via ``refresh_token`` when available, else by re-issuing the
    client_credentials grant for machine-to-machine callers) and retries once.
    """

    def __init__(
        self,
        issuer_url: str = _DEFAULT_ISSUER,
        client_id: str = None,
        client_secret: str = None,
        redirect_uri: Optional[str] = None,
        timeout: float = 10.0,
        session: Optional[requests.Session] = None,
    ):
        self.issuer_url = issuer_url.rstrip("/")
        self.client_id = client_id
        self.client_secret = client_secret or ""
        self.redirect_uri = redirect_uri
        self.timeout = timeout
        self._session = session or requests.Session()

        self._access_token: Optional[str] = None
        self._refresh_token: Optional[str] = None
        self._expires_at: float = 0.0
        self._cc_scope: Optional[str] = None
        self._cc_used: bool = False

    def authorization_url(self, state: Optional[str] = None, scope: Optional[str] = None) -> str:
        params = {
            "response_type": "code",
            "client_id": self.client_id,
        }
        if self.redirect_uri:
            params["redirect_uri"] = self.redirect_uri
        if scope:
            params["scope"] = scope
        if state:
            params["state"] = state
        return f"{self.issuer_url}{_AUTHORIZE_ENDPOINT}?{urlencode(params)}"

    def client_credentials(self, scope: Optional[str] = None) -> TokenResponse:
        data = {"grant_type": "client_credentials"}
        if scope:
            data["scope"] = scope
        token = self._request_token(data)
        self._cc_scope = scope
        self._cc_used = True
        return token

    def exchange_code(self, code: str, redirect_uri: Optional[str] = None) -> TokenResponse:
        redirect = redirect_uri or self.redirect_uri
        data = {"grant_type": "authorization_code", "code": code}
        if redirect:
            data["redirect_uri"] = redirect
        return self._request_token(data)

    def refresh_token(self, refresh_token: Optional[str] = None) -> TokenResponse:
        rt = refresh_token or self._refresh_token
        if not rt:
            raise TokenError("No refresh_token available; call exchange_code first.", 0)
        return self._request_token({"grant_type": "refresh_token", "refresh_token": rt})

    def publish_message(
        self, request_or_token, request: Optional[PublishRequest] = None
    ) -> PublishResponse:
        # Support both signatures: publish_message(request) using the cached
        # token (with auto-refresh), and publish_message(token, request) with
        # an explicit token.
        if isinstance(request_or_token, PublishRequest):
            return self._publish_with_cached_token(request_or_token)
        return self._publish_once(request_or_token, request)

    def _publish_with_cached_token(self, request: PublishRequest) -> PublishResponse:
        if self._access_token is None:
            raise PlatformClientError(
                "No access token cached. Call client_credentials()/exchange_code() first, "
                "or use publish_message(token, request).",
                0,
            )
        if self._needs_refresh():
            self._proactively_refresh()
        try:
            return self._publish_once(self._access_token, request)
        except PlatformClientError as e:
            if e.status_code != 401:
                raise
            self._proactively_refresh()
            return self._publish_once(self._access_token, request)

    def _publish_once(self, access_token: str, request: PublishRequest) -> PublishResponse:
        url = f"{self.issuer_url}{_PUBLISH_ENDPOINT}"
        try:
            resp = self._session.post(
                url,
                json=request.to_dict(),
                headers={
                    "Authorization": f"Bearer {access_token}",
                    "Accept": "application/json",
                },
                timeout=self.timeout,
            )
        except requests.RequestException as e:
            raise PlatformClientError(f"Publish request failed: {e}", 0) from e
        return self._parse_publish_response(resp)

    def _request_token(self, data: dict) -> TokenResponse:
        url = f"{self.issuer_url}{_TOKEN_ENDPOINT}"
        try:
            resp = self._session.post(
                url,
                data=data,
                auth=(self.client_id, self.client_secret),
                headers={"Accept": "application/json"},
                timeout=self.timeout,
            )
        except requests.RequestException as e:
            raise TokenError(f"Token request failed: {e}", 0) from e

        if resp.status_code < 200 or resp.status_code >= 300:
            raise TokenError(
                f"Token endpoint returned {resp.status_code}: {resp.text}",
                resp.status_code,
            )
        try:
            payload = resp.json()
        except ValueError as e:
            raise TokenError(f"Malformed token response: {resp.text}", 0) from e

        token = TokenResponse(
            access_token=payload.get("access_token"),
            token_type=payload.get("token_type", "Bearer"),
            expires_in=payload.get("expires_in"),
            refresh_token=payload.get("refresh_token"),
            scope=payload.get("scope"),
        )
        if token.refresh_token:
            self._refresh_token = token.refresh_token
        ttl = (token.expires_in or 0) - _CLOCK_SKEW_SECONDS
        self._access_token = token.access_token
        self._expires_at = time.time() + max(0, ttl)
        return token

    def _parse_publish_response(self, resp: requests.Response) -> PublishResponse:
        if resp.status_code == 401:
            raise PlatformClientError("Unauthorized (401)", 401)
        if resp.status_code < 200 or resp.status_code >= 300:
            raise PlatformClientError(
                f"Publish returned {resp.status_code}: {resp.text}", resp.status_code
            )
        try:
            body = resp.json()
        except ValueError as e:
            raise PlatformClientError(f"Malformed publish response: {resp.text}", 0) from e
        data = body.get("data", body) if isinstance(body, dict) else {}
        return PublishResponse.from_dict(data)

    def _needs_refresh(self) -> bool:
        return self._access_token is None or time.time() >= self._expires_at

    def _proactively_refresh(self):
        if self._refresh_token:
            self.refresh_token(self._refresh_token)
            return
        if self._cc_used:
            self.client_credentials(self._cc_scope)
            return
        raise PlatformClientError(
            "Access token expired and no refresh_token is available. Re-authenticate.", 401
        )
