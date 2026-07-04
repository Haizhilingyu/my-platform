package com.example.audit.repository;

import com.example.audit.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** 审计日志 Repository。继承 {@link JpaSpecificationExecutor} 支持多条件动态过滤分页。 */
public interface AuditLogRepository
    extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {}
