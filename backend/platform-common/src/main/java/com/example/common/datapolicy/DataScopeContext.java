package com.example.common.datapolicy;

import com.example.common.security.CurrentUser;
import java.util.Collections;
import java.util.Set;

/**
 * 数据权限上下文（ThreadLocal）。
 *
 * <p>由鉴权/拦截层在请求开始时填充，业务层通过 {@link #get()} 读取当前线程的数据权限范围， {@link DataScopeSpecification} 据此生成 JPA
 * {@code Predicate}。请求结束时必须调用 {@link #clear()} 释放，避免线程池串号。
 *
 * <p>设计上与 {@link CurrentUser} 同构：静态 ThreadLocal 持有不可变记录，避免可变状态泄漏。
 */
public final class DataScopeContext {

  private static final ThreadLocal<ScopeData> HOLDER = new ThreadLocal<>();

  private DataScopeContext() {}

  /**
   * 设置当前线程的数据权限上下文。
   *
   * @param scope 数据范围（非 null）
   * @param unitId 当前用户所属单位 ID（UNIT / UNIT_BELOW 使用）
   * @param customUnitIds 自定义单位集合（CUSTOM 使用），可为 null
   * @param userId 当前用户 ID（SELF 使用），可为 null
   */
  public static void set(DataScope scope, Long unitId, Set<Long> customUnitIds, Long userId) {
    HOLDER.set(new ScopeData(scope, unitId, customUnitIds, userId));
  }

  /** 获取当前线程的数据权限上下文；未设置时返回 null。 */
  public static ScopeData get() {
    return HOLDER.get();
  }

  /** 清除当前线程的数据权限上下文。 */
  public static void clear() {
    HOLDER.remove();
  }

  /**
   * 不可变权限数据快照。{@link #customUnitIds()} 返回不可修改视图，防御外部篡改。
   *
   * @param scope 数据范围
   * @param unitId 单位 ID
   * @param customUnitIds 自定义单位集合
   * @param userId 用户 ID
   */
  public record ScopeData(DataScope scope, Long unitId, Set<Long> customUnitIds, Long userId) {
    public ScopeData {
      if (customUnitIds == null) {
        customUnitIds = Collections.emptySet();
      } else {
        customUnitIds = Collections.unmodifiableSet(customUnitIds);
      }
    }
  }
}
