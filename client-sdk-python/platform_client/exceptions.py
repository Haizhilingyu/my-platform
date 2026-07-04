"""SDK exceptions."""


class PlatformClientError(Exception):
    """Raised on any SDK failure. Carries the HTTP status code when applicable."""

    def __init__(self, message, status_code=0):
        super().__init__(message)
        self.status_code = status_code


class TokenError(PlatformClientError):
    """Raised when a token grant or refresh fails."""
