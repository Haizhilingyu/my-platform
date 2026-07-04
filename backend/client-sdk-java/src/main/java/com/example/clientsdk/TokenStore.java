package com.example.clientsdk;

/**
 * Caches the most recently obtained access token together with its absolute expiry time, so the
 * client can decide when a proactive refresh is required without re-issuing a token request on
 * every call. Thread-safe via synchronization on the instance.
 */
public class TokenStore {

  private String accessToken;
  private String refreshToken;
  private long expiresAtMillis;
  private final long clockSkewSeconds;

  public TokenStore() {
    this(10L);
  }

  public TokenStore(long clockSkewSeconds) {
    this.clockSkewSeconds = clockSkewSeconds;
  }

  public synchronized void store(String accessToken, String refreshToken, Long expiresInSeconds) {
    this.accessToken = accessToken;
    if (refreshToken != null) {
      this.refreshToken = refreshToken;
    }
    long ttl = expiresInSeconds == null ? 0L : Math.max(0L, expiresInSeconds - clockSkewSeconds);
    this.expiresAtMillis = System.currentTimeMillis() + ttl * 1000L;
  }

  public synchronized boolean needsRefresh() {
    return accessToken == null || System.currentTimeMillis() >= expiresAtMillis;
  }

  public synchronized String getAccessToken() {
    return accessToken;
  }

  public synchronized String getRefreshToken() {
    return refreshToken;
  }

  public synchronized void clear() {
    accessToken = null;
    refreshToken = null;
    expiresAtMillis = 0L;
  }
}
