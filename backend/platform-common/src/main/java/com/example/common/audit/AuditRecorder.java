package com.example.common.audit;

/**
 * 审计事件落库 SPI。由 {@link AuditAspect} 在请求线程同步调用，实现方负责异步持久化。
 *
 * <p>采用 SPI 抽象（而非让 platform-common 直接依赖 audit 模块）以保持依赖方向： platform-common 是底层公共模块，不应反向依赖业务模块。audit
 * 模块实现此接口， Spring 按类型自动装配。当 classpath 上无 audit 模块时，切面将降级为静默跳过 （见 {@link AuditAspect} 的空实现回退）。
 */
public interface AuditRecorder {

  /**
   * 异步记录一条审计事件。实现应标注 {@code @Async} 以在独立线程池执行，确保不阻塞请求线程。
   *
   * @param event 已脱敏、组装完毕的审计事件，非 null
   */
  void record(AuditEvent event);
}
