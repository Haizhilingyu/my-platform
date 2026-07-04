package com.example.audit.controller;

import com.example.audit.dto.AuditLogQuery;
import com.example.audit.dto.AuditLogVO;
import com.example.audit.service.AuditLogService;
import com.example.common.result.PageResult;
import com.example.common.result.Result;
import com.example.common.security.RequiresPermission;
import com.example.common.web.PageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 审计日志查询 Controller。 */
@Tag(name = "审计日志", description = "查询系统审计日志")
@RestController
@RequestMapping("/sys/audit")
@RequiredArgsConstructor
public class AuditLogController {

  private final AuditLogService auditLogService;

  @RequiresPermission("sys:audit:list")
  @Operation(summary = "分页查询审计日志")
  @GetMapping("/logs")
  public Result<PageResult<AuditLogVO>> list(
      @RequestParam(required = false) String actor,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String result,
      @RequestParam(required = false) String targetType,
      @RequestParam(required = false) String targetId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime startTime,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime endTime,
      @RequestParam(defaultValue = "1") int pageNum,
      @RequestParam(defaultValue = "20") int pageSize) {

    var filter = new AuditLogQuery(actor, action, result, targetType, targetId, startTime, endTime);
    var pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    return Result.ok(PageUtils.toPageResult(auditLogService.query(filter, pageable)));
  }
}
