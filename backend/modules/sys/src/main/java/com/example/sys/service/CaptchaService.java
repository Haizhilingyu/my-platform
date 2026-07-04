package com.example.sys.service;

import cn.hutool.captcha.LineCaptcha;
import cn.hutool.captcha.CaptchaUtil;
import com.example.common.cache.RedisCacheService;
import com.example.sys.dto.CaptchaResult;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 图形验证码服务。
 *
 * <p>生成（{@link #generate()}）使用 Hutool {@link LineCaptcha}，答案存 Redis（key=
 * {@code captcha:{captchaId}}，TTL 5 分钟）。
 *
 * <p>校验（{@link #validate(String, String)}）命中后立即删除 Redis key，确保单次使用——
 * 同一 captchaId 第二次校验必然失败，防止重放攻击。
 */
@Service
@RequiredArgsConstructor
public class CaptchaService {

    static final String CAPTCHA_KEY_PREFIX = "captcha:";
    static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);

    private final RedisCacheService redisCacheService;

    public CaptchaResult generate() {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(200, 70, 4, 30);
        String captchaId = UUID.randomUUID().toString();
        String code = captcha.getCode();
        redisCacheService.set(CAPTCHA_KEY_PREFIX + captchaId, code, CAPTCHA_TTL);
        return new CaptchaResult(captchaId, captcha.getImageBase64Data());
    }

    /**
     * 校验验证码。命中后立即删除 key（单次使用，防重放）。
     *
     * @return true=校验通过；false=captchaId/captchaCode 为空、key 不存在（已过期或已用）、答案不匹配
     */
    public boolean validate(String captchaId, String captchaCode) {
        if (captchaId == null || captchaCode == null) {
            return false;
        }
        Optional<String> stored = redisCacheService.get(CAPTCHA_KEY_PREFIX + captchaId, String.class);
        if (stored.isEmpty()) {
            return false;
        }
        boolean ok = stored.get().equalsIgnoreCase(captchaCode);
        if (ok) {
            redisCacheService.delete(CAPTCHA_KEY_PREFIX + captchaId);
        }
        return ok;
    }
}
