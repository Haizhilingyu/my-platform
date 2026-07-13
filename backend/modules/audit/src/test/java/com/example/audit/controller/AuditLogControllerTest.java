package com.example.audit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.audit.dto.AuditLogQuery;
import com.example.audit.dto.AuditLogVO;
import com.example.audit.service.AuditLogService;
import com.example.common.result.PageResult;
import com.example.common.result.Result;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

/** AuditLogController API 集成测试：验证请求参数 → AuditLogQuery 构造 → 服务调用 → Result 响应的完整链路。 */
@DisplayName("AuditLogController 请求-响应链路")
class AuditLogControllerTest {

  private final AuditLogService auditLogService = mock(AuditLogService.class);
  private final AuditLogController controller = new AuditLogController(auditLogService);

  @Test
  @DisplayName("list：透传查询条件构造 AuditLogQuery，返回 PageResult")
  void list_buildsQueryAndReturnsPage() {
    // Given
    Page<AuditLogVO> page = new PageImpl<>(List.of());
    when(auditLogService.query(any(AuditLogQuery.class), any())).thenReturn(page);

    // When
    Result<PageResult<AuditLogVO>> result =
        controller.list("alice", "LOGIN", "SUCCESS", "USER", "1", null, null, 1, 20);

    // Then
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data().list()).isEmpty();
    ArgumentCaptor<AuditLogQuery> cap = ArgumentCaptor.forClass(AuditLogQuery.class);
    verify(auditLogService).query(cap.capture(), any());
    AuditLogQuery q = cap.getValue();
    assertThat(q.actor()).isEqualTo("alice");
    assertThat(q.action()).isEqualTo("LOGIN");
    assertThat(q.result()).isEqualTo("SUCCESS");
    assertThat(q.targetType()).isEqualTo("USER");
    assertThat(q.targetId()).isEqualTo("1");
  }

  @Test
  @DisplayName("list：无过滤条件时仍以空 filter 调用查询并成功返回")
  void list_withoutFilters_returnsPage() {
    when(auditLogService.query(any(AuditLogQuery.class), any()))
        .thenReturn(new PageImpl<>(List.of()));

    Result<PageResult<AuditLogVO>> result =
        controller.list(null, null, null, null, null, null, null, 1, 20);

    assertThat(result.isSuccess()).isTrue();
    verify(auditLogService).query(any(AuditLogQuery.class), any());
  }

  @Test
  @DisplayName("list 带时间范围：startTime/endTime 透传至 AuditLogQuery")
  void list_withTimeRange_buildsQuery() {
    when(auditLogService.query(any(AuditLogQuery.class), any()))
        .thenReturn(new PageImpl<>(List.of()));

    LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");
    LocalDateTime end = LocalDateTime.parse("2025-12-31T23:59:59");
    Result<PageResult<AuditLogVO>> result =
        controller.list(null, null, null, null, null, start, end, 1, 20);

    assertThat(result.isSuccess()).isTrue();
    ArgumentCaptor<AuditLogQuery> cap = ArgumentCaptor.forClass(AuditLogQuery.class);
    verify(auditLogService).query(cap.capture(), any());
    AuditLogQuery q = cap.getValue();
    assertThat(q.startTime()).isEqualTo(start);
    assertThat(q.endTime()).isEqualTo(end);
  }
}
