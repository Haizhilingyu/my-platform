package com.example.sys.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.ForbiddenException;
import com.example.common.result.Result;
import com.example.common.security.CurrentUser;
import com.example.sys.dto.SessionInfo;
import com.example.sys.service.SessionService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SessionController 在线会话端点")
class SessionControllerTest {

    private final SessionService sessionService = mock(SessionService.class);
    private final SessionController controller = new SessionController(sessionService);

    private SessionInfo info(String jti, Long userId) {
        return new SessionInfo(jti, userId, "admin", "10.0.0.1",
                "Chrome", "Chrome", LocalDateTime.now(), LocalDateTime.now().plusHours(1));
    }

    @BeforeEach
    void setCurrentUser() {
        CurrentUser.set(new CurrentUser.UserInfo(
                1L, "admin", 10L, java.util.Set.of("ADMIN"), java.util.Set.of()));
    }

    @AfterEach
    void clearCurrentUser() {
        CurrentUser.clear();
    }

    @Test
    @DisplayName("GET /sys/auth/sessions → 返回当前用户会话列表")
    void mySessions_returnsCurrentUserSessions() {
        when(sessionService.listSessions(1L)).thenReturn(List.of(info("jti-1", 1L)));

        Result<List<SessionInfo>> result = controller.mySessions();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).jti()).isEqualTo("jti-1");
    }

    @Test
    @DisplayName("POST /sys/auth/sessions/{jti}/revoke → 撤销自己的会话")
    void revokeMySession_ownSession_succeeds() {
        when(sessionService.getSession("jti-1")).thenReturn(Optional.of(info("jti-1", 1L)));

        Result<Void> result = controller.revokeMySession("jti-1");

        assertThat(result.isSuccess()).isTrue();
        verify(sessionService).revokeSession("jti-1");
    }

    @Test
    @DisplayName("POST /sys/auth/sessions/{jti}/revoke → 撤销他人会话 → 403")
    void revokeMySession_otherUserSession_forbidden() {
        when(sessionService.getSession("jti-x")).thenReturn(Optional.of(info("jti-x", 999L)));

        assertThatThrownBy(() -> controller.revokeMySession("jti-x"))
                .isInstanceOf(ForbiddenException.class);

        verify(sessionService, never()).revokeSession(any());
    }

    @Test
    @DisplayName("POST /sys/auth/sessions/{jti}/revoke → 会话不存在 → 403")
    void revokeMySession_notFound_forbidden() {
        when(sessionService.getSession("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.revokeMySession("ghost"))
                .isInstanceOf(ForbiddenException.class);
        verify(sessionService, never()).revokeSession(any());
    }

    @Test
    @DisplayName("GET /sys/user/{id}/sessions → 返回目标用户会话（管理员）")
    void userSessions_returnsTargetUserSessions() {
        when(sessionService.listSessions(2L)).thenReturn(List.of(info("jti-2", 2L)));

        Result<List<SessionInfo>> result = controller.userSessions(2L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.data()).hasSize(1);
        verify(sessionService).listSessions(2L);
    }

    @Test
    @DisplayName("POST /sys/user/{id}/sessions/{jti}/revoke → 管理员强制踢出")
    void revokeUserSession_adminForceKickout() {
        Result<Void> result = controller.revokeUserSession(2L, "jti-2");

        assertThat(result.isSuccess()).isTrue();
        verify(sessionService).revokeSession("jti-2");
    }
}
