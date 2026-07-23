package com.example.aiagent.agent.brain;

import com.example.aiagent.chat.dto.HistoryMessage;
import com.example.common.ai.AgentTool;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock 大脑：基于关键词意图匹配决定调用哪个工具，无需真实 LLM。
 *
 * <p>仅在 {@code app.ai.provider=mock}（默认，{@code matchIfMissing=true}）时生效。 完全数据驱动——从 {@link
 * AgentTool#triggerKeywords()} 匹配用户消息，命中后用反射从工具的 {@code inputSchemaType} record 泛化提取参数。新增工具只需在
 * provider 中声明关键词与 schema，Mock 自动适配。
 */
@Component
@ConditionalOnProperty(
    prefix = "app.ai",
    name = "provider",
    havingValue = "mock",
    matchIfMissing = true)
public class MockAgentBrain implements AgentBrain {

  private static final Pattern NUMBER = Pattern.compile("\\d+");
  private static final Pattern WORD = Pattern.compile("[a-zA-Z0-9_]+");

  @Override
  public BrainDecision decide(
      String userMessage, List<AgentTool> tools, List<HistoryMessage> relevantHistory) {
    // Mock 只对当前消息做关键词匹配；relevantHistory 仅 DeepSeek 使用，此处忽略。
    String msg = userMessage == null ? "" : userMessage.trim();
    String lower = msg.toLowerCase(Locale.ROOT);
    for (AgentTool t : tools) {
      if (matchesAny(lower, t.triggerKeywords())) {
        Map<String, Object> args = extractArgs(msg, t.inputSchemaType());
        return BrainDecision.tool(t.name(), args);
      }
    }
    return BrainDecision.reply(helpText(tools));
  }

  @Override
  public String summarize(String userMessage, String toolName, String toolResult) {
    return ""; // Mock 直接展示工具结果，无需 LLM 二次总结
  }

  /** 检查 msg（已小写）是否包含任一 keyword（也小写）。 */
  private static boolean matchesAny(String lowerMsg, String[] keywords) {
    if (keywords == null || keywords.length == 0) {
      return false;
    }
    for (String kw : keywords) {
      if (kw != null && !kw.isBlank() && lowerMsg.contains(kw.toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }

  /**
   * 用反射读 schemaType 的 record components，从用户消息中泛化提取参数。
   *
   * <p>Mock 是开发兜底，不追求 100% 精确——DeepSeek 才做精确意图提取。
   */
  private static Map<String, Object> extractArgs(String msg, Class<?> schemaType) {
    Map<String, Object> args = new HashMap<>();
    if (schemaType == null || !schemaType.isRecord()) {
      return args;
    }
    List<Long> allNums = allLongs(msg);
    int numIdx = 0;
    RecordComponent[] components = schemaType.getRecordComponents();
    for (RecordComponent rc : components) {
      String name = rc.getName();
      Class<?> type = rc.getType();
      if (type == Long.class || type == long.class || type == Integer.class || type == int.class) {
        // ID 字段：按出现序取数字
        if (numIdx < allNums.size()) {
          args.put(name, allNums.get(numIdx));
          numIdx++;
        }
      } else if (type == List.class || type.getName().contains("List")) {
        // List 字段（如 roleIds/menuIds/ids）：取全部数字
        if (name.endsWith("Ids") || name.endsWith("ids") || name.equals("ids")) {
          args.put(name, allNums);
        }
      } else if (type == String.class || type == Object.class) {
        // String 字段：对特定名称做提取
        String extracted = extractString(msg, name);
        if (extracted != null) {
          args.put(name, extracted);
        }
      }
      // Boolean/Optional 等不填，工具端兜底
    }
    return args;
  }

  /** 对特定 String 字段名从消息中提取值（Mock 简化版）。 */
  private static String extractString(String msg, String fieldName) {
    if (fieldName.equals("keyword") || fieldName.equals("path") || fieldName.equals("jti")) {
      return null; // 这些字段 Mock 不提取，工具端默认不限
    }
    // 尝试在消息中找紧跟关键词后的单词
    List<String> prefixes = new ArrayList<>();
    prefixes.add(fieldName);
    String translated = translateFieldName(fieldName);
    if (translated != null) {
      prefixes.addAll(List.of(translated.split("\\|")));
    }
    for (String prefix : prefixes) {
      if (prefix == null || prefix.isBlank()) continue;
      Pattern p = Pattern.compile(prefix + "[^\\w]*([a-zA-Z0-9_@.]+)", Pattern.CASE_INSENSITIVE);
      Matcher m = p.matcher(msg);
      if (m.find()) {
        return m.group(1);
      }
    }
    return null;
  }

  /** 字段名中英映射（Mock 提取中文消息中的值）。 */
  private static String translateFieldName(String fieldName) {
    return switch (fieldName) {
      case "username" -> "用户名|用户|username|user";
      case "password" -> "密码|password";
      case "newPassword" -> "密码|password";
      case "roleCode" -> "编码|role.?code";
      case "roleName" -> "角色名|role.?name";
      case "unitCode" -> "编码|unit.?code";
      case "unitName" -> "名称|unit.?name";
      case "menuName" -> "菜单名|menu.?name";
      case "configKey" -> "键名|config.?key";
      case "key" -> "键名|key";
      default -> null;
    };
  }

  /** 生成帮助文本：列出所有可见工具的 description 首行。 */
  private static String helpText(List<AgentTool> tools) {
    StringBuilder sb = new StringBuilder("（Mock 助手）我可以帮你做以下操作，试试说：\n");
    for (AgentTool t : tools) {
      // 取 description 第一句（第一个句号前）
      String firstSentence = t.description();
      int dot = firstSentence.indexOf('。');
      if (dot > 0) {
        firstSentence = firstSentence.substring(0, dot);
      }
      sb.append("• ").append(firstSentence).append('\n');
    }
    return sb.toString().stripTrailing();
  }

  private static Long firstLong(String s) {
    Matcher m = NUMBER.matcher(s);
    return m.find() ? Long.valueOf(m.group()) : null;
  }

  private static List<Long> allLongs(String s) {
    List<Long> result = new ArrayList<>();
    Matcher m = NUMBER.matcher(s);
    while (m.find()) {
      result.add(Long.valueOf(m.group()));
    }
    return result;
  }
}
