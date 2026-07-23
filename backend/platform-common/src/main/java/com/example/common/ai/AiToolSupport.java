package com.example.common.ai;

import com.example.common.exception.ForbiddenException;
import com.example.common.security.CurrentUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 工具共享辅助方法：权限兜底校验 + 参数类型转换。
 *
 * <p>供所有模块的 {@link AiToolProvider} 实现复用，避免重复。
 */
public final class AiToolSupport {

  private AiToolSupport() {}

  /**
   * 权限兜底校验（第二层保障，防 LLM 幻觉调用）。admin 通配 {@code *} 直接放行。
   *
   * <p>第一层保障在 {@code ToolRegistry.toolsForCurrentUser()}——按权限过滤可见工具集。此方法在执行体 内兜底：即使 LLM
   * 幻觉编造了工具调用，缺权限仍抛 {@link ForbiddenException}。
   */
  public static void requirePermission(String permission) {
    if (permission == null) {
      return;
    }
    Set<String> perms = CurrentUser.getPermissions();
    if (!(perms.contains("*") || perms.contains(permission))) {
      throw ForbiddenException.i18n("error.permission.denied", permission);
    }
  }

  /** 提取必填字符串参数，空则抛 IllegalArgumentException。 */
  public static String str(Map<String, Object> args, String key) {
    Object v = args.get(key);
    if (v == null || v.toString().isBlank()) {
      throw new IllegalArgumentException("缺少参数 " + key);
    }
    return v.toString();
  }

  /** 提取可选字符串参数，null 安全。 */
  public static String strOrNull(Map<String, Object> args, String key) {
    Object v = args.get(key);
    return v == null ? null : v.toString();
  }

  /** 提取字符串参数，null/空时返回 fallback。 */
  public static String strOr(Map<String, Object> args, String key, String fallback) {
    Object v = args.get(key);
    if (v == null || v.toString().isBlank()) {
      return fallback;
    }
    return v.toString();
  }

  /** 提取 Long 参数（Number 或可解析的 String），null 安全。 */
  public static Long longOrNull(Map<String, Object> args, String key) {
    Object v = args.get(key);
    if (v == null) {
      return null;
    }
    if (v instanceof Number n) {
      return n.longValue();
    }
    return Long.valueOf(v.toString());
  }

  /** 提取 Integer 参数（Number 或可解析的 String），null 安全。 */
  public static Integer intOrNull(Map<String, Object> args, String key) {
    Object v = args.get(key);
    if (v == null) {
      return null;
    }
    if (v instanceof Number n) {
      return n.intValue();
    }
    return Integer.valueOf(v.toString());
  }

  /** 提取 int 参数，null/缺失时返回 fallback。 */
  public static int intOr(Map<String, Object> args, String key, int fallback) {
    Object v = args.get(key);
    if (v == null) {
      return fallback;
    }
    if (v instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(v.toString());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  /** 提取 List<Long> 参数（List&lt;Number&gt; 或 List&lt;String&gt;），空安全。 */
  @SuppressWarnings("unchecked")
  public static List<Long> longList(Map<String, Object> args, String key) {
    Object v = args.get(key);
    if (v == null) {
      return List.of();
    }
    if (v instanceof List<?> list) {
      List<Long> result = new ArrayList<>(list.size());
      for (Object item : list) {
        if (item instanceof Number n) {
          result.add(n.longValue());
        } else if (item != null) {
          result.add(Long.valueOf(item.toString()));
        }
      }
      return result;
    }
    return List.of();
  }
}
