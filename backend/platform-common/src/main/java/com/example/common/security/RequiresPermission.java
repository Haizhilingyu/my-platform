package com.example.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解。标注在 Controller 方法上，拦截器自动校验当前用户是否拥有指定权限。
 *
 * <p>权限标识规范：{@code 模块:资源:操作}，如 {@code sys:user:add}
 *
 * <p>示例：
 * <pre>
 * &#64;RequiresPermission("sys:user:add")
 * &#64;RequiresPermission(value = {"sys:user:add", "sys:user:edit"}, logical = Logical.OR)
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /** 权限标识，支持多个。 */
    String[] value();

    /** 多个权限之间的逻辑关系，默认 AND。 */
    Logical logical() default Logical.AND;
}
