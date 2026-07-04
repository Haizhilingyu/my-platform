package com.example.common.datapolicy;

/**
 * 数据权限范围。
 *
 * <p>与 {@code SysRole.dataScope} 字段（String）的取值一一对应，用于运行期类型安全的范围判定。
 *
 * <ul>
 *   <li>{@link #ALL} — 全部数据，无限制
 *   <li>{@link #UNIT} — 仅本单位
 *   <li>{@link #UNIT_BELOW} — 本单位及下属单位（由递归 CTE 解析后代 ID）
 *   <li>{@link #SELF} — 仅本人创建的数据（按 {@code created_by} 过滤）
 *   <li>{@link #CUSTOM} — 自定义单位集合
 * </ul>
 */
public enum DataScope {
  ALL,
  UNIT,
  UNIT_BELOW,
  SELF,
  CUSTOM;

  /**
   * 从 {@code SysRole.dataScope} 字符串解析为枚举，忽略大小写；无法识别时回退为 {@link #SELF}（最保守）。
   *
   * @param text 角色表中存储的范围文本（如 "ALL"、"unit_below"），可为 null
   * @return 对应枚举值
   */
  public static DataScope fromString(String text) {
    if (text == null || text.isBlank()) {
      return SELF;
    }
    try {
      return DataScope.valueOf(text.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      return SELF;
    }
  }
}
