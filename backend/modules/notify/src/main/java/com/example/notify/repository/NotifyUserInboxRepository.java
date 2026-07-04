package com.example.notify.repository;

import com.example.notify.domain.NotifyUserInbox;
import com.example.notify.enums.MessageLevel;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

  long countByUserIdAndReadStatusFalse(Long userId);

  Optional<NotifyUserInbox> findByIdAndUserId(Long id, Long userId);

  List<NotifyUserInbox> findByIdInAndUserId(List<Long> ids, Long userId);

  // JOIN on plain Long FK (no @ManyToOne) — Hibernate 6 supports this via theta-style join.
  @Query(
      """
      SELECT i FROM NotifyUserInbox i
      JOIN NotifyMessage m ON i.messageId = m.id
      WHERE i.userId = :userId
        AND (:level IS NULL OR m.level = :level)
        AND (:readStatus IS NULL OR i.readStatus = :readStatus)
        AND (:keyword IS NULL OR :keyword = ''
             OR LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
             OR LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')))
      """)
  Page<NotifyUserInbox> findInboxPage(
      @Param("userId") Long userId,
      @Param("level") MessageLevel level,
      @Param("readStatus") Boolean readStatus,
      @Param("keyword") String keyword,
      Pageable pageable);
}
