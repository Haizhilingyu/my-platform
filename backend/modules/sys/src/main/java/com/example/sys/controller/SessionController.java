package com.example.sys.controller;

import com.example.common.exception.ForbiddenException;
import com.example.common.i18n.Messages;
import com.example.common.result.Result;
import com.example.common.security.CurrentUser;
import com.example.common.security.RequiresPermission;
import com.example.sys.dto.SessionInfo;
import com.example.sys.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "在线会话", description = "查看活跃会话、强制踢出")
@RestController
@RequiredArgsConstructor
public class SessionController {

  private final SessionService sessionService;

  @Operation(summary = "当前用户活跃会话列表")
  @GetMapping("/api/sys/auth/sessions")
  public Result<List<SessionInfo>> mySessions() {
    Long userId = CurrentUser.getUserId();
    return Result.ok(sessionService.listSessions(userId));
  }

  @Operation(summary = "撤销自己的会话")
  @PostMapping("/api/sys/auth/sessions/{jti}/revoke")
  public Result<Void> revokeMySession(@PathVariable String jti) {
    Long userId = CurrentUser.getUserId();
    SessionInfo info =
        sessionService
            .getSession(jti)
            .orElseThrow(() -> new ForbiddenException(Messages.get("session.not.found")));
    if (!userId.equals(info.userId())) {
      throw new ForbiddenException(Messages.get("session.revoke.forbidden"));
    }
    sessionService.revokeSession(jti);
    return Result.ok();
  }

  @Operation(summary = "查看指定用户活跃会话（管理员）")
  @RequiresPermission("sys:user:session")
  @GetMapping("/api/sys/user/{id}/sessions")
  public Result<List<SessionInfo>> userSessions(@PathVariable Long id) {
    return Result.ok(sessionService.listSessions(id));
  }

  @Operation(summary = "强制踢出指定用户会话（管理员）")
  @RequiresPermission("sys:user:session")
  @PostMapping("/api/sys/user/{id}/sessions/{jti}/revoke")
  public Result<Void> revokeUserSession(@PathVariable Long id, @PathVariable String jti) {
    sessionService.revokeSession(jti);
    return Result.ok();
  }
}
