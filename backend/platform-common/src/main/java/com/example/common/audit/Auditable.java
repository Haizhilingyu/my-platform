package com.example.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解。标注在方法上，{@link AuditAspect} 会自动记录调用上下文 （操作人、IP、参数、结果/异常）并异步写入审计日志表。
 *
 * <p>标注在 Controller 方法上是最典型的用法，例如：
 *
 * <pre>
 * &#64;Auditable(action = "LOGIN")
 * &#64;PostMapping("/login")
 * public Result&lt;LoginVO&gt; login(@RequestBody LoginDTO dto) { ... }
 * </pre>
 *
 * <p>{@code targetType} 与 {@code targetIdParam} 为可选项：当方法操作某个具体资源时， 指定资源类型与「承载资源 ID
 * 的参数名」（可以是方法参数名或路径变量名），切面会提取 该参数值的 {@code toString()} 作为 {@code targetId}；未指定则留空。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

  /** 操作类型标识，建议全大写短语，如 {@code LOGIN}、{@code USER_CREATE}。 */
  String action();

  /** 操作对象类型，如 {@code USER}、{@code ROLE}。 默认空串表示不指定（由业务自行从参数推断或留空）。 */
  String targetType() default "";

  /**
   * 承载目标资源 ID 的方法参数名，如 {@code id}、{@code userId}。 切面按此名称在 {@code joinPoint.getArgs()} 中查找并取其 {@code
   * toString()}。 默认空串表示不记录目标 ID。
   */
  String targetIdParam() default "";
}
