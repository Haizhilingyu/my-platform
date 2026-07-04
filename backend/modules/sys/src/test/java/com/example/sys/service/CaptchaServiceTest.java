package com.example.sys.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.cache.RedisCacheService;
import com.example.sys.dto.CaptchaResult;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("CaptchaService 图形验证码（单次使用）")
class CaptchaServiceTest {

    private final RedisCacheService redisCacheService = mock(RedisCacheService.class);
    private final CaptchaService service = new CaptchaService(redisCacheService);

    @Nested
    @DisplayName("generate 生成验证码")
    class Generate {

        @Test
        @DisplayName("返回 captchaId（UUID）和带 data URI 前缀的 base64 图片")
        void generate_returnsIdAndBase64Image() {
            CaptchaResult result = service.generate();

            assertThat(result.captchaId()).isNotBlank();
            assertThat(result.captchaId()).matches("[0-9a-f-]{36}");
            assertThat(result.image()).startsWith("data:image/png;base64,");
            assertThat(result.image().length()).isGreaterThan("data:image/png;base64,".length() + 50);
        }

        @Test
        @DisplayName("答案以 captcha:{captchaId} 为 key 写入 Redis，TTL = 5 分钟")
        void generate_storesAnswerInRedisWithTtl() {
            CaptchaResult result = service.generate();

            ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
            verify(redisCacheService)
                    .set(eq(CaptchaService.CAPTCHA_KEY_PREFIX + result.captchaId()),
                            codeCaptor.capture(),
                            eq(CaptchaService.CAPTCHA_TTL));
            String storedCode = codeCaptor.getValue();
            assertThat(storedCode).isNotBlank();
            assertThat(storedCode.length()).isEqualTo(4);
            assertThat(CaptchaService.CAPTCHA_TTL).isEqualTo(Duration.ofMinutes(5));
        }

        @Test
        @DisplayName("每次 generate 生成不同的 captchaId")
        void generate_producesUniqueIds() {
            CaptchaResult r1 = service.generate();
            CaptchaResult r2 = service.generate();

            assertThat(r1.captchaId()).isNotEqualTo(r2.captchaId());
        }
    }

    @Nested
    @DisplayName("validate 校验验证码（单次使用）")
    class Validate {

        @Test
        @DisplayName("答案正确 → 返回 true，且立即删除 Redis key（防重放）")
        void validate_correctCode_returnsTrueAndDeletesKey() {
            String captchaId = "id-1";
            when(redisCacheService.get(CaptchaService.CAPTCHA_KEY_PREFIX + captchaId, String.class))
                    .thenReturn(Optional.of("ABCD"));

            boolean ok = service.validate(captchaId, "abcd");

            assertThat(ok).isTrue();
            verify(redisCacheService).delete(CaptchaService.CAPTCHA_KEY_PREFIX + captchaId);
        }

        @Test
        @DisplayName("答案大小写不敏感（ABCD vs abcd 视为匹配）")
        void validate_isCaseInsensitive() {
            when(redisCacheService.get(CaptchaService.CAPTCHA_KEY_PREFIX + "id", String.class))
                    .thenReturn(Optional.of("aBcD"));

            assertThat(service.validate("id", "AbCd")).isTrue();
        }

        @Test
        @DisplayName("同一 captchaId 第二次校验 → 返回 false（重放被拒）")
        void validate_sameIdTwice_secondFails() {
            String captchaId = "id-replay";
            when(redisCacheService.get(CaptchaService.CAPTCHA_KEY_PREFIX + captchaId, String.class))
                    .thenReturn(Optional.of("ABCD"))
                    .thenReturn(Optional.empty());

            boolean first = service.validate(captchaId, "abcd");
            boolean second = service.validate(captchaId, "abcd");

            assertThat(first).isTrue();
            assertThat(second).isFalse();
            verify(redisCacheService).delete(CaptchaService.CAPTCHA_KEY_PREFIX + captchaId);
        }

        @Test
        @DisplayName("验证码已过期/不存在 → 返回 false，不调用 delete")
        void validate_expiredOrMissing_returnsFalseWithoutDelete() {
            when(redisCacheService.get(CaptchaService.CAPTCHA_KEY_PREFIX + "expired", String.class))
                    .thenReturn(Optional.empty());

            boolean ok = service.validate("expired", "abcd");

            assertThat(ok).isFalse();
            verify(redisCacheService, never()).delete(CaptchaService.CAPTCHA_KEY_PREFIX + "expired");
        }

        @Test
        @DisplayName("答案错误 → 返回 false，不删除 key（允许用户重试同一验证码）")
        void validate_wrongCode_returnsFalseAndKeepsKey() {
            when(redisCacheService.get(CaptchaService.CAPTCHA_KEY_PREFIX + "id", String.class))
                    .thenReturn(Optional.of("ABCD"));

            boolean ok = service.validate("id", "WRONG");

            assertThat(ok).isFalse();
            verify(redisCacheService, never()).delete(CaptchaService.CAPTCHA_KEY_PREFIX + "id");
        }

        @Test
        @DisplayName("captchaId 为 null → 返回 false，不查 Redis")
        void validate_nullCaptchaId_returnsFalse() {
            assertThat(service.validate(null, "abcd")).isFalse();
            verify(redisCacheService, never()).get(eq(CaptchaService.CAPTCHA_KEY_PREFIX + "null"), eq(String.class));
        }

        @Test
        @DisplayName("captchaCode 为 null → 返回 false，不查 Redis")
        void validate_nullCaptchaCode_returnsFalse() {
            assertThat(service.validate("id", null)).isFalse();
            verify(redisCacheService, never())
                    .get(CaptchaService.CAPTCHA_KEY_PREFIX + "id", String.class);
        }
    }
}
