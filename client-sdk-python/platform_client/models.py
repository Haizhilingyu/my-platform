"""Data models for the platform client SDK."""
from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import List, Optional


class MessageLevel(str, Enum):
    """Message urgency. ``URGENT`` triggers an immediate WebSocket push."""

    URGENT = "URGENT"
    IMPORTANT = "IMPORTANT"
    NORMAL = "NORMAL"


class RecipientType(str, Enum):
    """Recipient scope: ``USER`` id, ``ROLE`` id, or ``UNIT`` id (with descendants)."""

    USER = "USER"
    ROLE = "ROLE"
    UNIT = "UNIT"


@dataclass
class Recipient:
    type: RecipientType
    id: int

    def to_dict(self):
        return {"type": self.type.value, "id": self.id}


@dataclass
class TokenResponse:
    access_token: str
    token_type: Optional[str] = "Bearer"
    expires_in: Optional[int] = None
    refresh_token: Optional[str] = None
    scope: Optional[str] = None


@dataclass
class PublishRequest:
    """Body for ``POST /openapi/notify/publish``.

    ``recipients`` is a list because the platform supports fan-out to multiple
    user / role / unit targets in a single publish call.
    """

    title: str
    content: str
    level: MessageLevel = MessageLevel.NORMAL
    business_type: Optional[str] = None
    expire_time: Optional[str] = None
    recipients: List[Recipient] = field(default_factory=list)

    @classmethod
    def urgent(cls, title: str, content: str) -> "PublishRequest":
        return cls(title=title, content=content, level=MessageLevel.URGENT)

    def add_recipient(self, type_: RecipientType, id_: int) -> "PublishRequest":
        self.recipients.append(Recipient(type_, id_))
        return self

    def to_dict(self) -> dict:
        body = {
            "title": self.title,
            "content": self.content,
            "level": self.level.value,
            "recipients": [r.to_dict() for r in self.recipients],
        }
        if self.business_type is not None:
            body["businessType"] = self.business_type
        if self.expire_time is not None:
            body["expireTime"] = self.expire_time
        return body


@dataclass
class PublishResponse:
    messageId: Optional[int] = None
    recipientCount: Optional[int] = None

    @classmethod
    def from_dict(cls, data: dict) -> "PublishResponse":
        return cls(
            messageId=data.get("messageId"),
            recipientCount=data.get("recipientCount"),
        )
