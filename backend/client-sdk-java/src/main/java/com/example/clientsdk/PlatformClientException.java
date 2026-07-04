package com.example.clientsdk;

/**
 * Thrown when any SDK operation fails (HTTP error, OAuth2 error, or token-refresh failure). Carries
 * the HTTP status code when applicable so callers can branch on it.
 */
public class PlatformClientException extends RuntimeException {

  private final int statusCode;

  public PlatformClientException(String message) {
    this(message, 0, null);
  }

  public PlatformClientException(String message, int statusCode) {
    this(message, statusCode, null);
  }

  public PlatformClientException(String message, int statusCode, Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
  }

  /** HTTP status code, or 0 if the failure was not HTTP-related (e.g. network error). */
  public int getStatusCode() {
    return statusCode;
  }
}
