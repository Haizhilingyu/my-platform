package com.example.common.login;

/**
 * 登录方式描述符。用于前端动态渲染登录页 Tab（账号密码 / LDAP / 扫码 等）。
 *
 * <p>由 {@link LoginMethodProvider#describe()} 返回，通过 {@code GET /sys/auth/login-methods} 对外暴露。前端根据
 * {@code order} 升序排列 Tab，根据 {@code icon} 选择展示图标。
 *
 * @param method 登录方式标识，如 "password"、"ldap"。全局唯一。
 * @param label 展示名称，如 "账号密码登录"、"企业 LDAP"。
 * @param icon 图标标识（前端按约定渲染，如 "password-icon"、"ldap-icon"）。
 * @param order 排序权重，升序排列（数值越小越靠前）。
 */
public record LoginMethodDescriptor(String method, String label, String icon, int order) {}
