package com.example.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 审计日志实体（append-only）。仅记录写入时间，无更新/操作人审计列。 */
@Entity
@Table(name = "audit_log")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(length = 64)
  private String actor;

  @Column(name = "actor_type", length = 20)
  private String actorType;

  @Column(nullable = false, length = 64)
  private String action;

  @Column(name = "target_type", length = 64)
  private String targetType;

  @Column(name = "target_id", length = 64)
  private String targetId;

  @Column(length = 64)
  private String ip;

  @Column(name = "user_agent", columnDefinition = "TEXT")
  private String userAgent;

  @Column(columnDefinition = "TEXT")
  private String params;

  @Column(nullable = false, length = 20)
  private String result;

  @Column(name = "error_msg", columnDefinition = "TEXT")
  private String errorMsg;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
