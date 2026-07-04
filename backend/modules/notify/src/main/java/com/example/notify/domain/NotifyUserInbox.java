package com.example.notify.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "notify_user_inbox",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_notify_inbox_user_seq",
            columnNames = {"user_id", "seq"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotifyUserInbox {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long userId;

  @Column(nullable = false)
  private Long messageId;

  @Column(nullable = false)
  private Long seq;

  @Column(nullable = false)
  @Builder.Default
  private Boolean delivered = false;

  private LocalDateTime deliveredAt;

  @Column(nullable = false)
  @Builder.Default
  private Boolean readStatus = false;

  private LocalDateTime readTime;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();
}
