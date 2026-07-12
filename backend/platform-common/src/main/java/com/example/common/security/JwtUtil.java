package com.example.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;

/** JWT 工具类。负责 Token 的生成、解析和校验。 */
public class JwtUtil {

  private final SecretKey key;
  private final long expirationMillis;

  public JwtUtil(String secret, long expirationMillis) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expirationMillis = expirationMillis;
  }

  /** 生成 Token。 */
  public String generate(Long userId, String username, List<String> roles) {
    return generate(userId, username, null, roles);
  }

  /** 生成 Token（携带 unitId）。 */
  public String generate(Long userId, String username, Long unitId, List<String> roles) {
    return generate(userId, username, unitId, roles, null);
  }

  /** 生成 Token（携带 unitId 和 locale）。locale 为 null/空白时不写入 claim，保持向后兼容。 */
  public String generate(
      Long userId, String username, Long unitId, List<String> roles, String locale) {
    Date now = new Date();
    var builder =
        Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(String.valueOf(userId))
            .claim("username", username)
            .claim("roles", roles);
    if (unitId != null) {
      builder.claim("unitId", unitId);
    }
    if (locale != null && !locale.isBlank()) {
      builder.claim("locale", locale);
    }
    return builder
        .issuedAt(now)
        .expiration(new Date(now.getTime() + expirationMillis))
        .signWith(key)
        .compact();
  }

  /** 解析 Token。 */
  public Claims parse(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }

  /** 从 Authorization header 中提取 token。 */
  public String extractToken(String authHeader) {
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      return authHeader.substring(7);
    }
    return null;
  }

  /** 校验 token 是否有效。 */
  public boolean isValid(String token) {
    try {
      Claims claims = parse(token);
      return claims.getExpiration().after(new Date());
    } catch (Exception e) {
      return false;
    }
  }
}
