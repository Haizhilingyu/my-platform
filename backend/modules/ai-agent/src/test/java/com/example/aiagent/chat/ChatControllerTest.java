package com.example.aiagent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.aiagent.agent.AgentService;
import com.example.aiagent.agent.event.AgentEvent;
import com.example.aiagent.chat.dto.ChatRequest;
import com.example.aiagent.chat.dto.HistoryMessage;
import com.example.aiagent.config.AgentProperties;
import com.example.common.exception.GlobalExceptionHandler;
import com.example.common.exception.NotFoundException;
import com.example.common.security.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * ChatController 集成测试（MockMvc standaloneSetup）。
 *
 * <p>验证 {@code /api/ai/chat} SSE 端点契约（含对话历史落库、相关历史注入、聚合 assistant 文本）与 {@code GET/DELETE
 * /api/ai/chat/history} 端点。standaloneSetup 隔离 Security 过滤链；挂 {@link GlobalExceptionHandler} 以验证 404
 * 路径。
 */
@DisplayName("ChatController SSE 对话端点 + 历史接口")
class ChatControllerTest {

  private AgentService agentService;
  private AgentProperties properties;
  private ChatRateLimiter rateLimiter;
  private ChatHistoryService chatHistoryService;
  private com.example.sys.SysApi sysApi;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    agentService = mock(AgentService.class);
    properties = mock(AgentProperties.class);
    rateLimiter = mock(ChatRateLimiter.class);
    chatHistoryService = mock(ChatHistoryService.class);
    sysApi = mock(com.example.sys.SysApi.class);
    when(properties.isEnabled()).thenReturn(true);
    when(properties.getProvider()).thenReturn("mock");
    when(rateLimiter.tryAcquire(any())).thenReturn(true);
    // 默认相关历史为空（不干扰事件转发断言）
    when(chatHistoryService.relevant(anyLong(), any())).thenReturn(List.of());
    mvc =
        MockMvcBuilders.standaloneSetup(
                new ChatController(
                    agentService, properties, rateLimiter, chatHistoryService, sysApi))
            .setControllerAdvice(new GlobalExceptionHandler(new StaticMessageSource()))
            .build();
    CurrentUser.set(new CurrentUser.UserInfo(1L, "tester", null, Set.of(), Set.of("*")));
  }

  @AfterEach
  void tearDown() {
    CurrentUser.clear();
  }

  /** 发起一次 chat 请求，返回 SSE 响应体（兼容同步完成与异步两种路径）。 */
  private String chatSse(String json) throws Exception {
    MvcResult result =
        mvc.perform(post("/api/ai/chat").contentType(MediaType.APPLICATION_JSON).content(json))
            .andReturn();
    if (result.getRequest().isAsyncStarted()) {
      result.getAsyncResult(3_000);
      return mvc.perform(asyncDispatch(result))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString(StandardCharsets.UTF_8);
    }
    return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("既无消息又无二次确认：返回 error 事件，不调用大脑")
  void emptyMessageAndNoConfirmReturnsErrorEvent() throws Exception {
    String body = chatSse("{}");

    assertThat(body).contains("event:error");
    verifyNoInteractions(agentService);
  }

  @Test
  @DisplayName("限流超出：同步返回 error 事件")
  void rateLimitedReturnsErrorEvent() throws Exception {
    when(rateLimiter.tryAcquire(any())).thenReturn(false);

    String body = chatSse("{\"message\":\"hi\"}");

    assertThat(body).contains("event:error");
    verifyNoInteractions(agentService);
  }

  @Test
  @DisplayName("AI 未启用：返回 error 事件")
  void disabledReturnsErrorEvent() throws Exception {
    when(properties.isEnabled()).thenReturn(false);

    String body = chatSse("{\"message\":\"hi\"}");

    assertThat(body).contains("event:error");
    verifyNoInteractions(agentService);
  }

  @Test
  @DisplayName("正常对话：转发 token/done 事件；user 与 assistant 消息各落库一次；传入相关历史")
  void normalFlowForwardsEventsAndPersists() throws Exception {
    when(agentService.handle(eq("hi"), any(List.class), isNull()))
        .thenReturn(List.of(AgentEvent.token("hello"), AgentEvent.done()));

    String body = chatSse("{\"message\":\"hi\"}");

    assertThat(body).contains("event:token").contains("hello").contains("event:done");
    verify(chatHistoryService).save(1L, "user", "hi");
    verify(chatHistoryService).save(1L, "assistant", "hello");
    verify(agentService).handle(eq("hi"), any(List.class), isNull());
    // relevant 被调用以选历史
    verify(chatHistoryService).relevant(1L, "hi");
  }

  @Test
  @DisplayName("破坏性工具 pending 轮：仅存 user 消息，不存 assistant（confirm 事件非 result/token）")
  void destructivePendingRoundPersistsUserOnly() throws Exception {
    when(agentService.handle(eq("删除用户 5"), any(List.class), isNull()))
        .thenReturn(
            List.of(
                AgentEvent.tool("deleteUser", Map.of("id", 5)),
                AgentEvent.confirm("deleteUser", Map.of("id", 5), "确认删除？"),
                AgentEvent.done()));

    chatSse("{\"message\":\"删除用户 5\"}");

    verify(chatHistoryService).save(1L, "user", "删除用户 5");
    verify(chatHistoryService, never()).save(eq(1L), eq("assistant"), any());
  }

  @Test
  @DisplayName("二次确认回执：不存 user 消息（message 非用户意图）；assistant 结果落库")
  void confirmRoundtripPersistsAssistantOnly() throws Exception {
    when(agentService.handle(any(), any(List.class), any(ChatRequest.ConfirmTool.class)))
        .thenReturn(List.of(AgentEvent.result("deleted"), AgentEvent.done()));

    chatSse("{\"message\":\"✓ 确认\",\"confirm\":{\"tool\":\"deleteUser\",\"args\":{\"id\":5}}}");

    verify(chatHistoryService, never()).save(any(), eq("user"), any());
    verify(chatHistoryService).save(1L, "assistant", "deleted");
  }

  @Test
  @DisplayName("消息超 1000 字符：校验失败 400")
  void oversizedMessageRejected() throws Exception {
    String oversized = "x".repeat(1001);

    mvc.perform(
            post("/api/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"" + oversized + "\"}"))
        .andExpect(status().isBadRequest());
  }

  // ===== 历史接口 =====

  @Test
  @DisplayName("GET /chat/history：返回当前用户最近 10 条（含 id）")
  void getHistoryReturnsRecentMessages() throws Exception {
    when(chatHistoryService.recent(1L)).thenReturn(List.of(new HistoryMessage(7L, "user", "历史问题")));

    mvc.perform(get("/api/ai/chat/history"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data[0].id").value(7))
        .andExpect(jsonPath("$.data[0].role").value("user"))
        .andExpect(jsonPath("$.data[0].text").value("历史问题"));
  }

  @Test
  @DisplayName("DELETE /chat/history/{id}：删除本人消息返回 200")
  void deleteOwnMessageSucceeds() throws Exception {
    mvc.perform(delete("/api/ai/chat/history/7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200));

    verify(chatHistoryService).delete(1L, 7L);
  }

  @Test
  @DisplayName("DELETE /chat/history/{id}：不存在/非本人时 404")
  void deleteMissingReturns404() throws Exception {
    // chatSse 路径吞异常；这里走标准 MockMvc，GlobalExceptionHandler 会把 NotFoundException 映射为 404
    org.mockito.Mockito.doThrow(new NotFoundException("消息不存在"))
        .when(chatHistoryService)
        .delete(1L, 999L);

    mvc.perform(delete("/api/ai/chat/history/999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404));
  }
}
