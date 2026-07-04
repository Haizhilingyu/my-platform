package com.example.audit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.audit.domain.AuditLog;
import com.example.audit.dto.AuditLogQuery;
import com.example.audit.dto.AuditLogVO;
import com.example.audit.repository.AuditLogRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * 审计日志查询过滤集成测试（H2）。
 *
 * <p>用真实 JPA + H2 验证 {@link AuditLogService#query} 的 Specification 过滤逻辑 （actor LIKE / action 精确 /
 * result 精确 / date range），而非仅委托验证。
 */
@DataJpaTest(properties = "spring.flyway.enabled=false")
@AutoConfigureTestDatabase
@Import(AuditLogQueryIntegrationTest.TestConfig.class)
@DisplayName("审计日志查询过滤")
class AuditLogQueryIntegrationTest {

  @Autowired private AuditLogRepository auditLogRepository;
  @Autowired private AuditLogService auditLogService;

  @TestConfiguration
  static class TestConfig {
    @org.springframework.context.annotation.Bean
    AuditLogService auditLogService(AuditLogRepository repo) {
      return new AuditLogService(repo);
    }
  }

  @Test
  @DisplayName("actor 模糊 + action 精确过滤：只返回匹配项")
  void should_filterByActorLikeAndActionExact() {
    save("alice", "LOGIN", "success", LocalDateTime.now().minusMinutes(10));
    save("alex", "LOGIN", "success", LocalDateTime.now().minusMinutes(5));
    save("bob", "LOGOUT", "success", LocalDateTime.now().minusMinutes(1));

    AuditLogQuery filter = new AuditLogQuery("al", "LOGIN", null, null, null, null, null);
    Page<AuditLogVO> page =
        auditLogService.query(
            filter, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

    assertThat(page.getContent()).hasSize(2);
    assertThat(page.getContent())
        .allSatisfy(
            vo ->
                assertThat(vo.actor()).startsWith("al").isEqualTo("al" + vo.actor().substring(2)));
    assertThat(page.getContent()).extracting(AuditLogVO::action).containsOnly("LOGIN");
  }

  @Test
  @DisplayName("result 过滤：只返回 fail")
  void should_filterByResult() {
    save("alice", "LOGIN", "success", LocalDateTime.now());
    save("alice", "LOGIN", "fail", LocalDateTime.now());
    save("alice", "LOGIN", "fail", LocalDateTime.now());

    AuditLogQuery filter = new AuditLogQuery(null, null, "fail", null, null, null, null);
    Page<AuditLogVO> page = auditLogService.query(filter, PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(2);
    assertThat(page.getContent()).allSatisfy(vo -> assertThat(vo.result()).isEqualTo("fail"));
  }

  @Test
  @DisplayName("date range 过滤：startTime/endTime 之间")
  void should_filterByDateRange() {
    LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
    save("alice", "LOGIN", "success", base); // 边界内（=start）
    save("alice", "LOGIN", "success", base.plusDays(1));
    save("alice", "LOGIN", "success", base.plusDays(5)); // 边界外（>end）

    AuditLogQuery filter = new AuditLogQuery(null, null, null, null, null, base, base.plusDays(2));
    Page<AuditLogVO> page = auditLogService.query(filter, PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(2);
  }

  @Test
  @DisplayName("无过滤条件：返回全部")
  void should_returnAll_when_noFilter() {
    save("alice", "LOGIN", "success", LocalDateTime.now());
    save("bob", "LOGOUT", "success", LocalDateTime.now());

    AuditLogQuery filter = new AuditLogQuery(null, null, null, null, null, null, null);
    Page<AuditLogVO> page = auditLogService.query(filter, PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(2);
  }

  private void save(String actor, String action, String result, LocalDateTime createdAt) {
    AuditLog log =
        AuditLog.builder()
            .actor(actor)
            .action(action)
            .result(result)
            .ip("127.0.0.1")
            .createdAt(createdAt)
            .build();
    auditLogRepository.saveAndFlush(log);
  }
}
