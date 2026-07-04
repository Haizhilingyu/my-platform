package com.example.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.audit.domain.AuditLog;
import com.example.audit.dto.AuditLogQuery;
import com.example.audit.dto.AuditLogVO;
import com.example.audit.repository.AuditLogRepository;
import com.example.common.audit.AuditEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/** {@link AuditLogService} 单元测试。覆盖 record 落库委托 + query 分页委托与 VO 映射。 */
@ExtendWith(MockitoExtension.class)
@DisplayName("审计日志服务")
class AuditLogServiceTest {

  @Mock private AuditLogRepository auditLogRepository;

  @InjectMocks private AuditLogService auditLogService;

  @Test
  @DisplayName("record：将 AuditEvent 转为 AuditLog 并调用 repository.save")
  void should_persistAuditEvent_onRecord() {
    LocalDateTime now = LocalDateTime.now();
    AuditEvent event =
        new AuditEvent(
            "alice",
            "USER",
            "LOGIN",
            null,
            null,
            "10.0.0.1",
            "Mozilla/5.0",
            "{\"username\":\"alice\",\"password\":\"***\"}",
            "success",
            null,
            now);

    auditLogService.record(event);

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogRepository).save(captor.capture());
    AuditLog saved = captor.getValue();
    assertThat(saved.getAction()).isEqualTo("LOGIN");
    assertThat(saved.getActor()).isEqualTo("alice");
    assertThat(saved.getResult()).isEqualTo("success");
    assertThat(saved.getCreatedAt()).isEqualTo(now);
    assertThat(saved.getParams()).contains("***");
  }

  @Test
  @DisplayName("query：委托 repository.findAll(spec, pageable) 并将结果映射为 VO")
  void should_delegateToRepositoryWithSpecification_andMapToVo() {
    AuditLogQuery filter = new AuditLogQuery("ali", "LOGIN", "success", null, null, null, null);
    Pageable pageable = PageRequest.of(0, 10);

    AuditLog entity =
        AuditLog.builder()
            .id(1L)
            .actor("alice")
            .action("LOGIN")
            .result("success")
            .ip("10.0.0.1")
            .params("{}")
            .createdAt(LocalDateTime.now())
            .build();
    Page<AuditLog> page = new PageImpl<>(java.util.List.of(entity), pageable, 1L);
    when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(page);

    org.springframework.data.domain.Page<AuditLogVO> result =
        auditLogService.query(filter, pageable);

    assertThat(result.getContent()).hasSize(1);
    AuditLogVO vo = result.getContent().get(0);
    assertThat(vo.id()).isEqualTo(1L);
    assertThat(vo.actor()).isEqualTo("alice");
    assertThat(vo.action()).isEqualTo("LOGIN");
    verify(auditLogRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  @DisplayName("record：落库异常被吞掉且仅记日志（不影响业务主流程）")
  void should_swallowPersistenceException() {
    AuditEvent event =
        new AuditEvent(
            "alice",
            "USER",
            "LOGIN",
            null,
            null,
            null,
            null,
            null,
            "success",
            null,
            LocalDateTime.now());
    when(auditLogRepository.save(any(AuditLog.class))).thenThrow(new RuntimeException("db down"));

    // 不抛异常——审计落库失败不得影响业务
    auditLogService.record(event);
    verify(auditLogRepository).save(any(AuditLog.class));
  }
}
