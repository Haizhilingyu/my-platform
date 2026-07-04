package com.example.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JwtUtil（jti/解析）")
class JwtUtilTest {

    private static final String SECRET = "012345678901234567890123456789012345678901234567890123456789";
    private static final long EXPIRATION_MS = 3600_000L;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, EXPIRATION_MS);
    }

    @Test
    @DisplayName("generate：token 携带 jti claim（UUID 格式）")
    void generate_shouldContainJtiClaim() {
        String token = jwtUtil.generate(1L, "admin", 10L, List.of("ADMIN"));

        Claims claims = jwtUtil.parse(token);

        String jti = claims.getId();
        assertThat(jti).isNotNull().isNotBlank();
        // UUID format: 8-4-4-4-12 hex groups
        assertThat(jti).matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    }

    @Test
    @DisplayName("generate：每次生成不同 jti（唯一性）")
    void generate_shouldProduceUniqueJti() {
        String t1 = jwtUtil.generate(1L, "admin", null, List.of("ADMIN"));
        String t2 = jwtUtil.generate(1L, "admin", null, List.of("ADMIN"));

        String jti1 = jwtUtil.parse(t1).getId();
        String jti2 = jwtUtil.parse(t2).getId();

        assertThat(jti1).isNotEqualTo(jti2);
    }

    @Test
    @DisplayName("generate：3 参重载仍生成 jti（向后兼容）")
    void generate_threeArgOverload_shouldContainJti() {
        String token = jwtUtil.generate(1L, "admin", List.of("ADMIN"));
        assertThat(jwtUtil.parse(token).getId()).isNotNull();
    }

    @Test
    @DisplayName("generate：unitId 写入 claim")
    void generate_shouldContainUnitId() {
        String token = jwtUtil.generate(1L, "admin", 42L, List.of("ADMIN"));
        assertThat(jwtUtil.parse(token).get("unitId", Long.class)).isEqualTo(42L);
    }

    @Test
    @DisplayName("generate：unitId=null 时不写 claim")
    void generate_unitIdNull_omitsClaim() {
        String token = jwtUtil.generate(1L, "admin", null, List.of("ADMIN"));
        assertThat(jwtUtil.parse(token).get("unitId", Long.class)).isNull();
    }

    @Test
    @DisplayName("isValid：有效 token → true；篡改签名 → false")
    void isValid_distinguishesValidAndTampered() {
        String token = jwtUtil.generate(1L, "admin", null, List.of("ADMIN"));
        assertThat(jwtUtil.isValid(token)).isTrue();
        // 篡改 payload 段（中间段）使签名失效
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "x." + parts[2];
        assertThat(jwtUtil.isValid(tampered)).isFalse();
        assertThat(jwtUtil.isValid("not.a.real.token")).isFalse();
    }

    @Test
    @DisplayName("extractToken：Bearer 前缀剥离；无前缀 → null")
    void extractToken_stripsBearerPrefix() {
        assertThat(jwtUtil.extractToken("Bearer abc.def")).isEqualTo("abc.def");
        assertThat(jwtUtil.extractToken("Basic abc")).isNull();
        assertThat(jwtUtil.extractToken(null)).isNull();
    }
}
