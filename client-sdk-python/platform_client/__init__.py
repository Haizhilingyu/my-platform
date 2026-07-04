"""OAuth2 + message-publish client for My Platform.

Public surface::

    from platform_client import PlatformClient, MessageLevel, RecipientType, PublishRequest

    client = PlatformClient("http://localhost:8090", client_id=..., client_secret=...)
    client.client_credentials("notify:publish")
    resp = client.publish_message(PublishRequest.urgent("title", "body")
                                  .add_recipient(RecipientType.USER, 1))
"""

from platform_client.client import PlatformClient
from platform_client.exceptions import PlatformClientError, TokenError
from platform_client.models import (
    MessageLevel,
    PublishRequest,
    PublishResponse,
    RecipientType,
    TokenResponse,
)

__all__ = [
    "PlatformClient",
    "PlatformClientError",
    "TokenError",
    "MessageLevel",
    "RecipientType",
    "PublishRequest",
    "PublishResponse",
    "TokenResponse",
]

__version__ = "1.0.0"
