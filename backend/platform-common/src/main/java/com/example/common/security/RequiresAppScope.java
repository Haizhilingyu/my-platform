package com.example.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * OAuth2 应用 scope 校验注解。标注在 {@code /openapi/**} Controller 方法上，由 {@link AppScopeAspect} 拦截，从当前
 * OAuth2 access_token（Bearer JWT）中提取 scope，校验是否包含所需 scope。
 *
 * <p>与 {@link RequiresPermission} 的区别：
 *
 * <ul>
 *   <li>{@code @RequiresPermission}：基于内部用户的角色/权限（{@link CurrentUser}）。
 *   <li>{@code @RequiresAppScope}：基于外部应用的 OAuth2 scope（access_token 中的 {@code scope} claim）。
 * </ul>
 *
 * <p>仅当请求经 OAuth2 Resource Server 认证（{@code /openapi/**} 链）后生效；非 OAuth2 上下文直接抛 403。
 *
 * <p>示例：
 *
 * <pre>
 * &#64;RequiresAppScope("notify:publish")
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresAppScope {

  /** 必需的 OAuth2 scope 名称，如 {@code "notify:publish"}。 */
  String value();
}
