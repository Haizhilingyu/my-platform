package com.example.notify.repository;

import com.example.notify.domain.NotifyUserInbox;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotifyUserInboxRepository extends JpaRepository<NotifyUserInbox, Long> {

  @Query("SELECT COALESCE(MAX(i.seq), 0) FROM NotifyUserInbox i WHERE i.userId = :userId")
  Long findMaxSeqByUserId(@Param("userId") Long userId);

  @Query(
      "SELECT i FROM NotifyUserInbox i "
          + "WHERE i.userId = :userId "
          + "AND i.seq > :lastSeq "
          + "AND i.createdAt > :since "
          + "ORDER BY i.seq ASC")
  List<NotifyUserInbox> findReplayCandidates(
      @Param("userId") Long userId,
      @Param("lastSeq") Long lastSeq,
      @Param("since") LocalDateTime since);

  List<NotifyUserInbox> findByUserIdOrderBySeqAsc(Long userId);
}
