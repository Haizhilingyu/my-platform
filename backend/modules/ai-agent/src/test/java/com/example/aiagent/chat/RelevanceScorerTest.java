package com.example.aiagent.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aiagent.chat.domain.AiChatMessage;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RelevanceScorer 相关度打分测试（纯函数，无 Spring 上下文）。
 *
 * <p>验证「3 条最相关」语义：按词面重叠（拉丁 token + 中文 bigram 交集）排序、上限、过滤零分、 同分取更近、输出时间正序。
 */
@DisplayName("RelevanceScorer 相关度打分")
class RelevanceScorerTest {

  private static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 1, 0, 0);

  @Test
  @DisplayName("按重叠度降序排列；零分被过滤")
  void ranksByOverlapAndFiltersZero() {
    // query「删除用户 42」tokens: 删除/除用/用户/42
    AiChatMessage weather = msg(1, "今天天气如何", T0); // 无重叠 → 过滤
    AiChatMessage create = msg(2, "创建用户 alice", T0.plusMinutes(1)); // 共享「用户」=1 分
    AiChatMessage delete = msg(3, "删除用户 5", T0.plusMinutes(2)); // 共享 删除/除用/用户=3 分

    List<AiChatMessage> result =
        RelevanceScorer.topRelevant(List.of(weather, create, delete), "删除用户 42", 3);

    assertThat(result).hasSize(2); // 零分 weather 被过滤
    // 排序：先按 score 降序 [delete(3), create(1)]，再按时间升序重排 → create(T+1) 在前, delete(T+2) 在后
    assertThat(result.get(0).getId()).isEqualTo(2L);
    assertThat(result.get(1).getId()).isEqualTo(3L);
  }

  @Test
  @DisplayName("上限 limit：5 条均有重叠时恰好返回分数最高的 3 条")
  void limitCapsAtTopThree() {
    AiChatMessage a = msg(1, "删除用户 5", T0); // 高分
    AiChatMessage b = msg(2, "删除用户 6", T0.plusMinutes(1)); // 高分
    AiChatMessage c = msg(3, "删除用户 7", T0.plusMinutes(2)); // 高分
    AiChatMessage d = msg(4, "用户 8", T0.plusMinutes(3)); // 低分（仅「用户」=1）
    AiChatMessage e = msg(5, "用户 9", T0.plusMinutes(4)); // 低分

    List<AiChatMessage> result = RelevanceScorer.topRelevant(List.of(a, b, c, d, e), "删除用户", 3);

    assertThat(result).hasSize(3);
    // 三条高分被选中，两条低分被排除
    assertThat(result).extracting(AiChatMessage::getId).containsExactlyInAnyOrder(1L, 2L, 3L);
    // 输出时间升序
    assertThat(result).extracting(AiChatMessage::getId).containsExactly(1L, 2L, 3L);
  }

  @Test
  @DisplayName("同分时取更近的一条；输出按时间升序")
  void tieBreaksByRecencyAndAscendingTime() {
    AiChatMessage older = msg(1, "查询用户", T0); // 同分
    AiChatMessage newer = msg(2, "查询用户", T0.plusMinutes(5)); // 同分

    // limit=1：两条同分。输入按仓库契约（最新在前），newer 在前，稳定排序应选 newer
    List<AiChatMessage> one = RelevanceScorer.topRelevant(List.of(newer, older), "查询用户", 1);
    assertThat(one).hasSize(1);
    assertThat(one.get(0).getId()).isEqualTo(2L);

    // limit=2：两条都选，输出时间升序（older 在前）
    List<AiChatMessage> two = RelevanceScorer.topRelevant(List.of(newer, older), "查询用户", 2);
    assertThat(two).hasSize(2);
    assertThat(two).extracting(AiChatMessage::getId).containsExactly(1L, 2L);
  }

  @Test
  @DisplayName("拉丁 token 匹配（list users ↔ users list）")
  void latinTokenMatch() {
    AiChatMessage m = msg(1, "users list", T0); // 共享 {list, users}
    List<AiChatMessage> result = RelevanceScorer.topRelevant(List.of(m), "list users", 3);
    assertThat(result).hasSize(1);
  }

  @Test
  @DisplayName("query 空白返回空表")
  void blankQueryReturnsEmpty() {
    AiChatMessage m = msg(1, "删除用户", T0);
    assertThat(RelevanceScorer.topRelevant(List.of(m), "", 3)).isEmpty();
    assertThat(RelevanceScorer.topRelevant(List.of(m), "   ", 3)).isEmpty();
    assertThat(RelevanceScorer.topRelevant(List.of(m), null, 3)).isEmpty();
  }

  @Test
  @DisplayName("候选 content 空白被跳过")
  void blankContentSkipped() {
    AiChatMessage blank = msg(1, "  ", T0);
    assertThat(RelevanceScorer.topRelevant(List.of(blank), "查询用户", 3)).isEmpty();
  }

  private static AiChatMessage msg(long id, String content, LocalDateTime createdAt) {
    return AiChatMessage.builder().id(id).content(content).createdAt(createdAt).build();
  }
}
