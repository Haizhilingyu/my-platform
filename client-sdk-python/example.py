"""Demo: client_credentials grant -> publish an URGENT message.

Run::

    python example.py http://localhost:8090 <clientId> <clientSecret> <recipientUserId>

Never hard-code secrets in source: pass them as arguments or env vars
(PLATFORM_CLIENT_ID / PLATFORM_CLIENT_SECRET).
"""
import os
import sys

from platform_client import (
    MessageLevel,
    PlatformClient,
    PublishRequest,
    RecipientType,
)


def main(argv):
    issuer = argv[1] if len(argv) > 1 else "http://localhost:8090"
    client_id = argv[2] if len(argv) > 2 else os.environ.get("PLATFORM_CLIENT_ID", "")
    client_secret = argv[3] if len(argv) > 3 else os.environ.get("PLATFORM_CLIENT_SECRET", "")
    recipient_id = int(argv[4]) if len(argv) > 4 else 1

    if not client_id or not client_secret:
        sys.exit("client_id and client_secret are required")

    client = PlatformClient(issuer, client_id=client_id, client_secret=client_secret)
    token = client.client_credentials("notify:publish")
    print(f"Got access token, expires_in={token.expires_in}s")

    request = (
        PublishRequest.urgent("SDK demo", "Hello from client-sdk-python")
        .add_recipient(RecipientType.USER, recipient_id)
    )
    result = client.publish_message(request)
    print(
        f"Published URGENT message id={result.messageId} "
        f"recipientCount={result.recipientCount}"
    )


if __name__ == "__main__":
    main(sys.argv)
