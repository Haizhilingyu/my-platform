package com.example.aiagent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.aiagent.chat.domain.AiChatMessage;
import com.example.aiagent.chat.dto.HistoryMessage;
import com.example.aiagent.chat.repository.AiChatMessageRepository;
import com.example.common.exception.NotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;

/**
 * ChatHistoryService 测试（Mockito mock repository）。
 *
 * <p>验证：recent 时间升序与字段映射；relevant 委托打分限 3 条且数据层异常降级空表；save 数据层异常 不向上抛；delete 不存在时抛
 * NotFoundException、命中时调用带归属删除。
 */
@DisplayName("ChatHistoryService 对话历史服务")
@ExtendWith(MockitoExtension.class)
class ChatHistoryServiceTest {

  private static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 1, 0, 0);

  @Mock AiChatMessageRepository repository;
  @InjectMocks ChatHistoryService service;

  @Test
  @DisplayName("recent：仓库返回降序，服务返回时间升序且 map 为 (id, role, content)")
  void recentReturnsAscendingWithMapping() {
    // 仓库返回 3 条，时间降序（id=3 最新）
    when(repository.findTop10ByUserIdOrderByCreatedAtDescIdDesc(1L))
        .thenReturn(
            List.of(
                msg(3, "user", "c", T0.plusMinutes(2)),
                msg(2, "assistant", "b", T0.plusMinutes(1)),
                msg(1, "user", "a", T0)));

    List<HistoryMessage> result = service.recent(1L);

    assertThat(result).hasSize(3);
    // 升序：id=1 在前，id=3 在后
    assertThat(result.get(0).id()).isEqualTo(1L);
    assertThat(result.get(1).id()).isEqualTo(2L);
    assertThat(result.get(2).id()).isEqualTo(3L);
    assertThat(result.get(0).role()).isEqualTo("user");
    assertThat(result.get(0).text()).isEqualTo("a");
  }

  @Test
  @DisplayName("recent：空列表返回空表")
  void recentEmptyReturnsEmpty() {
    when(repository.findTop10ByUserIdOrderByCreatedAtDescIdDesc(1L)).thenReturn(List.of());
    assertThat(service.recent(1L)).isEmpty();
  }

  @Test
  @DisplayName("relevant：数据层正常时委托打分并限 3 条")
  void relevantDelegatesToScorer() {
    // 5 条都有重叠，打分应只返回 3 条
    List<AiChatMessage> candidates =
        IntStream.rangeClosed(1, 5)
            .mapToObj(i -> msg(i, "user", "删除用户 " + i, T0.plusMinutes(i)))
            .toList();
    when(repository.findTop100ByUserIdOrderByCreatedAtDescIdDesc(1L)).thenReturn(candidates);

    List<HistoryMessage> result = service.relevant(1L, "删除用户");

    assertThat(result).hasSize(3);
  }

  @Test
  @DisplayName("relevant：数据层异常降级为空表，不向上抛")
  void relevantDegradesOnDataAccessException() {
    DataAccessException ex = new QueryTimeoutException("timeout");
    when(repository.findTop100ByUserIdOrderByCreatedAtDescIdDesc(1L)).thenThrow(ex);

    assertThat(service.relevant(1L, "查询用户")).isEmpty();
  }

  @Test
  @DisplayName("save：数据层异常不向上抛（历史落库失败不得中断对话）")
  void saveSilentlyIgnoresDataAccessException() {
    doThrow(new QueryTimeoutException("timeout")).when(repository).save(any(AiChatMessage.class));

    // 不抛异常
    service.save(1L, "user", "你好");

    verify(repository).save(any(AiChatMessage.class));
  }

  @Test
  @DisplayName("delete：命中时调用带归属删除，不抛异常")
  void deleteRemovesOwnedMessage() {
    when(repository.deleteByIdAndUserId(9L, 1L)).thenReturn(1L);

    service.delete(1L, 9L);

    verify(repository).deleteByIdAndUserId(9L, 1L);
  }

  @Test
  @DisplayName("delete：不存在或不属于该用户时抛 NotFoundException（统一 404）")
  void deleteThrowsNotFoundWhenMissingOrForeign() {
    when(repository.deleteByIdAndUserId(999L, 1L)).thenReturn(0L);

    assertThatThrownBy(() -> service.delete(1L, 999L)).isInstanceOf(NotFoundException.class);
  }

  private static AiChatMessage msg(long id, String role, String content, LocalDateTime createdAt) {
    return AiChatMessage.builder()
        .id(id)
        .userId(1L)
        .role(role)
        .content(content)
        .createdAt(createdAt)
        .build();
  }
}
