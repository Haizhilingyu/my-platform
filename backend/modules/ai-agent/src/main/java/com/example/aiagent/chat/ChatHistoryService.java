package com.example.aiagent.chat;

import com.example.aiagent.chat.domain.AiChatMessage;
import com.example.aiagent.chat.dto.HistoryMessage;
import com.example.aiagent.chat.repository.AiChatMessageRepository;
import com.example.common.exception.NotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 对话历史服务：最近 N 条读取、相关历史挑选、消息落库、单条删除。
 *
 * <p>写降级哲学（同 {@code ChatRateLimiter}）：{@link #save} 与 {@link #relevant} 吞 {@link
 * DataAccessException} 仅告警，不中断对话；显式读/删操作（{@link #recent}、{@link #delete}） 不吞异常，诚实报错。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatHistoryService {

  /** 注入给大脑的相关历史条数上限。 */
  static final int CONTEXT_RELEVANT_LIMIT = 3;

  private final AiChatMessageRepository repository;

  /** 最近 10 条（时间升序，含消息 id 供前端单条删除）。读失败让其抛出（诚实报错）。 */
  public List<HistoryMessage> recent(Long userId) {
    List<AiChatMessage> desc = repository.findTop10ByUserIdOrderByCreatedAtDescIdDesc(userId);
    return toAscending(desc);
  }

  /** 从最近 100 条中选出与当前消息最相关的 3 条（时间正序）。数据层异常降级为空表，不中断对话。 */
  public List<HistoryMessage> relevant(Long userId, String currentMessage) {
    try {
      List<AiChatMessage> desc = repository.findTop100ByUserIdOrderByCreatedAtDescIdDesc(userId);
      List<AiChatMessage> picked =
          RelevanceScorer.topRelevant(desc, currentMessage, CONTEXT_RELEVANT_LIMIT);
      return picked.stream()
          .map(m -> new HistoryMessage(m.getId(), m.getRole(), m.getContent()))
          .toList();
    } catch (DataAccessException e) {
      log.warn("加载 AI 相关历史失败，降级为空上下文: {}", e.getMessage());
      return List.of();
    }
  }

  /** 落库一条消息；数据层异常仅告警，不向上抛（历史落库失败不得让对话失败）。 */
  public void save(Long userId, String role, String content) {
    try {
      repository.save(AiChatMessage.builder().userId(userId).role(role).content(content).build());
    } catch (DataAccessException e) {
      log.warn("保存 AI 对话历史失败（已忽略）: {}", e.getMessage());
    }
  }

  /** 单条删除（带归属校验）。不存在或不属于该用户时抛 {@link NotFoundException}（统一 404，不泄露他人数据存在性）。 */
  @Transactional
  public void delete(Long userId, Long messageId) {
    if (repository.deleteByIdAndUserId(messageId, userId) == 0) {
      throw new NotFoundException("消息不存在");
    }
  }

  /** 将时间降序列表反转为时间升序，并 map 为 HistoryMessage。 */
  private static List<HistoryMessage> toAscending(List<AiChatMessage> desc) {
    if (desc == null || desc.isEmpty()) {
      return List.of();
    }
    List<HistoryMessage> result = new ArrayList<>(desc.size());
    for (int i = desc.size() - 1; i >= 0; i--) {
      AiChatMessage m = desc.get(i);
      result.add(new HistoryMessage(m.getId(), m.getRole(), m.getContent()));
    }
    return Collections.unmodifiableList(result);
  }
}
