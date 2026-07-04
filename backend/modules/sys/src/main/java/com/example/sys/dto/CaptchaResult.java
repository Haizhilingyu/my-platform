package com.example.sys.dto;

/** 图形验证码响应 DTO。captchaId 由前端原样回传用于校验；image 为带 data URI 前缀的 base64 图片。 */
public record CaptchaResult(String captchaId, String image) {}
