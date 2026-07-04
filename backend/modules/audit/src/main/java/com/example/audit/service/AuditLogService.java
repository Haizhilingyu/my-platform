package com.example.audit.service;

import com.example.audit.domain.AuditLog;
import com.example.audit.dto.AuditLogQuery;
import com.example.audit.dto.AuditLogVO;
import com.example.audit.repository.AuditLogRepository;
import com.example.common.audit.AuditEvent;
import com.example.common.audit.AuditRecorder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计日志服务：异步写入 + 多条件分页查询。
 *
 * <p>实现 {@link AuditRecorder} SPI，由 {@code com.example.common.audit.AuditAspect} 通过 Spring
 * 自动装配调用。{@link #record(AuditEvent)} 标注 {@code @Async}，在独立线程池落库， 不阻塞请求线程。
 */
@Service
@RequiredArgsConstructor
public class AuditLogService implements AuditRecorder {

  private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

  private final AuditLogRepository auditLogRepository;

  /** 异步落库一条审计事件。{@code @Async} 使其在独立线程执行，调用方立即返回。 方法签名返回 {@code void}、抛出异常仅记日志——审计落库失败绝不影响业务主流程。 */
  @Async
  @Override
  @Transactional
  public void record(AuditEvent event) {
    try {
      AuditLog entity =
          AuditLog.builder()
              .actor(event.actor())
              .actorType(event.actorType())
              .action(event.action())
              .targetType(nullIfBlank(event.targetType()))
              .targetId(nullIfBlank(event.targetId()))
              .ip(event.ip())
              .userAgent(event.userAgent())
              .params(event.params())
              .result(event.result())
              .errorMsg(event.errorMsg())
              .createdAt(event.occurredAt())
              .build();
      auditLogRepository.save(entity);
    } catch (RuntimeException ex) {
      log.warn("审计日志异步落库失败 action={} actor={}: {}", event.action(), event.actor(), ex.getMessage());
    }
  }

  @Transactional(readOnly = true)
  public Page<AuditLogVO> query(AuditLogQuery filter, Pageable pageable) {
    Specification<AuditLog> spec =
        (root, query, cb) -> {
          var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
          if (filter.actor() != null && !filter.actor().isBlank()) {
            predicates.add(
                cb.like(cb.lower(root.get("actor")), "%" + filter.actor().toLowerCase() + "%"));
          }
          if (filter.action() != null && !filter.action().isBlank()) {
            predicates.add(cb.equal(root.get("action"), filter.action()));
          }
          if (filter.result() != null && !filter.result().isBlank()) {
            predicates.add(cb.equal(root.get("result"), filter.result()));
          }
          if (filter.targetType() != null && !filter.targetType().isBlank()) {
            predicates.add(cb.equal(root.get("targetType"), filter.targetType()));
          }
          if (filter.targetId() != null && !filter.targetId().isBlank()) {
            predicates.add(cb.equal(root.get("targetId"), filter.targetId()));
          }
          if (filter.startTime() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.startTime()));
          }
          if (filter.endTime() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.endTime()));
          }
          return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    return auditLogRepository.findAll(spec, pageable).map(AuditLogVO::of);
  }

  private static String nullIfBlank(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
