package com.example.aiagent.chat;

import com.example.aiagent.chat.domain.AiChatMessage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 历史消息相关度打分（词面重叠）。纯静态纯函数，无 Spring 依赖，便于单测。
 *
 * <p>无 embedding 基础设施，采用确定的词面重叠：拉丁 token（连续字母数字整体）+ 中文 bigram （每段汉字取滑动 2 字子串），相关分 = query 与候选
 * content 两个 token 集的交集大小。
 */
final class RelevanceScorer {

  private static final Pattern LATIN = Pattern.compile("[a-z0-9]+");
  private static final Pattern CJK = Pattern.compile("[一-龥]+");
  private static final int TEXT_TRUNCATE = 200;

  private RelevanceScorer() {}

  /**
   * 从候选集中选出与 query 最相关的前 {@code limit} 条，按时间正序返回（供 Prompt 拼装）。
   *
   * @param candidates 候选消息（调用方保证最新在前，使同分稳定排序时更近者胜出）
   * @param query 当前用户消息
   * @param limit 最多返回条数
   */
  static List<AiChatMessage> topRelevant(List<AiChatMessage> candidates, String query, int limit) {
    if (query == null || query.isBlank() || candidates == null || candidates.isEmpty()) {
      return List.of();
    }
    Set<String> queryTokens = tokenize(query);
    if (queryTokens.isEmpty()) {
      return List.of();
    }
    record Scored(AiChatMessage msg, int score) {}
    List<Scored> scored = new ArrayList<>();
    for (AiChatMessage c : candidates) {
      if (c == null || c.getContent() == null || c.getContent().isBlank()) {
        continue;
      }
      Set<String> contentTokens = tokenize(c.getContent());
      int score = 0;
      for (String t : queryTokens) {
        if (contentTokens.contains(t)) {
          score++;
        }
      }
      if (score > 0) {
        scored.add(new Scored(c, score));
      }
    }
    if (scored.isEmpty()) {
      return List.of();
    }
    // 按 score 降序稳定排序（输入最新在前，同分自然更近者胜出）
    scored.sort(Comparator.comparingInt((Scored s) -> s.score).reversed());
    List<AiChatMessage> top = new ArrayList<>();
    int n = Math.min(limit, scored.size());
    for (int i = 0; i < n; i++) {
      top.add(scored.get(i).msg);
    }
    // 拼装 Prompt 时需时间正序，按 (createdAt, id) 升序重排
    top.sort(
        Comparator.comparing(
                AiChatMessage::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AiChatMessage::getId, Comparator.nullsLast(Comparator.naturalOrder())));
    return top;
  }

  /** 分词：小写化后提取拉丁 token 与中文 bigram，汇总为 Set。 */
  private static Set<String> tokenize(String text) {
    String lower = text.toLowerCase(Locale.ROOT);
    // 先截断，避免超长文本无意义计算
    if (lower.length() > TEXT_TRUNCATE) {
      lower = lower.substring(0, TEXT_TRUNCATE);
    }
    Set<String> tokens = new HashSet<>();
    Matcher latin = LATIN.matcher(lower);
    while (latin.find()) {
      tokens.add(latin.group());
    }
    Matcher cjk = CJK.matcher(lower);
    while (cjk.find()) {
      String seg = cjk.group();
      if (seg.length() == 1) {
        tokens.add(seg);
      } else {
        for (int i = 0; i < seg.length() - 1; i++) {
          tokens.add(seg.substring(i, i + 2));
        }
      }
    }
    return tokens;
  }
}
