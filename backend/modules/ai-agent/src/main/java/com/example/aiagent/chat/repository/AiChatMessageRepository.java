package com.example.aiagent.chat.repository;

import com.example.aiagent.chat.domain.AiChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** AI 对话消息仓库。仅提供读取与单条删除派生查询——不提供批量清空。 */
public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

  /** 最近 10 条（时间降序、id 降序兜底同瞬）。 */
  List<AiChatMessage> findTop10ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

  /** 候选池（最近 100 条），供相关度打分挑选。 */
  List<AiChatMessage> findTop100ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

  /** 单条删除（带归属校验）。返回受影响行数，0 表示不存在或不属于该用户。 */
  @Modifying
  @Query("DELETE FROM AiChatMessage m WHERE m.id = :id AND m.userId = :userId")
  long deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
