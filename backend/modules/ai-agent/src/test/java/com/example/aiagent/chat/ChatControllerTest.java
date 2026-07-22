package com.example.aiagent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.aiagent.agent.AgentService;
import com.example.aiagent.agent.event.AgentEvent;
import com.example.aiagent.chat.dto.ChatRequest;
import com.example.aiagent.config.AgentProperties;
import com.example.common.security.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * ChatController 集成测试（MockMvc standaloneSetup）。
 *
 * <p>验证 {@code /api/ai/chat} SSE 端点的完整契约：限流拦截、入参兜底、enabled 开关、事件转发与二次确认回执。
 * 用 standaloneSetup（不启动 Spring 上下文）隔离 GlobalExceptionHandler / Security 过滤链，聚焦 Controller 本身；
 * AgentService / AgentProperties / ChatRateLimiter 以 Mock 替换，CurrentUser 在请求线程预置。
 *
 * <p>这是 ai-agent 模块此前缺失的 Controller 层集成测试——对比 sys 模块每个 Controller 均有 {@code *ControllerTest}，
 * ChatController 作为模块唯一对外端点此前零覆盖。
 */
@DisplayName("ChatController SSE 对话端点")
class ChatControllerTest {

  private AgentService agentService;
  private AgentProperties properties;
  private ChatRateLimiter rateLimiter;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    agentService = mock(AgentService.class);
    properties = mock(AgentProperties.class);
    rateLimiter = mock(ChatRateLimiter.class);
    when(properties.isEnabled()).thenReturn(true);
    when(rateLimiter.tryAcquire(any())).thenReturn(true);
    mvc =
        MockMvcBuilders.standaloneSetup(new ChatController(agentService, properties, rateLimiter))
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
  @DisplayName("正常对话：转发 token/done 事件序列")
  void normalFlowForwardsEvents() throws Exception {
    when(agentService.handle(eq("hi"), isNull()))
        .thenReturn(List.of(AgentEvent.token("hello"), AgentEvent.done()));

    String body = chatSse("{\"message\":\"hi\"}");

    assertThat(body).contains("event:token").contains("hello").contains("event:done");
  }

  @Test
  @DisplayName("二次确认回执：直接交给 AgentService 执行（不再过大脑）")
  void confirmRoundtripForwardsToService() throws Exception {
    when(agentService.handle(isNull(), any(ChatRequest.ConfirmTool.class)))
        .thenReturn(List.of(AgentEvent.result("deleted"), AgentEvent.done()));

    String body = chatSse("{\"confirm\":{\"tool\":\"deleteUser\",\"args\":{\"id\":5}}}");

    assertThat(body).contains("event:result");
    verify(agentService).handle(isNull(), any(ChatRequest.ConfirmTool.class));
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
}
