package com.example.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.security.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

/**
 * {@link AuditAspect} 单元测试。
 *
 * <p>覆盖：action 正确捕获、成功/失败两种路径都产生审计事件、密码参数脱敏、嵌套 DTO 敏感字段脱敏。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("审计切面")
class AuditAspectTest {

  @Mock private ObjectProvider<AuditRecorder> recorderProvider;
  @Mock private AuditRecorder recorder;
  @Mock private ProceedingJoinPoint joinPoint;
  @Mock private MethodSignature signature;

  private AuditAspect aspect;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    aspect = new AuditAspect(recorderProvider, objectMapper);
    CurrentUser.clear();
  }

  @AfterEach
  void tearDown() {
    CurrentUser.clear();
  }

  // ---- 测试目标方法（用反射拿到带 @Auditable 的 Method） ----

  public static class TargetService {
    @Auditable(action = "LOGIN")
    public String login(String username, String password) {
      return "ok";
    }

    @Auditable(action = "USER_UPDATE", targetType = "USER", targetIdParam = "id")
    public String update(Long id, CredentialDto cred) {
      return "ok";
    }

    @Auditable(action = "WILL_FAIL")
    public String boom() {
      throw new IllegalStateException("boom!");
    }
  }

  public static class CredentialDto {
    private final String username;
    private final String password;
    private final String captchaCode;

    public CredentialDto(String username, String password, String captchaCode) {
      this.username = username;
      this.password = password;
      this.captchaCode = captchaCode;
    }

    public String getUsername() {
      return username;
    }

    public String getPassword() {
      return password;
    }

    public String getCaptchaCode() {
      return captchaCode;
    }
  }

  private Method method(String name, Class<?>... params) throws NoSuchMethodException {
    return TargetService.class.getMethod(name, params);
  }

  @Test
  @DisplayName("成功路径：捕获 action=LOGIN 且 recorder 被调用一次")
  void should_captureAction_onSuccess() throws Throwable {
    when(recorderProvider.getIfAvailable()).thenReturn(recorder);
    when(joinPoint.getSignature()).thenReturn(signature);
    when(signature.getMethod()).thenReturn(method("login", String.class, String.class));
    when(signature.getParameterNames()).thenReturn(new String[] {"username", "password"});
    when(joinPoint.getArgs()).thenReturn(new Object[] {"alice", "s3cret"});
    when(joinPoint.proceed()).thenReturn("ok");

    Object result =
        aspect.around(
            joinPoint, method("login", String.class, String.class).getAnnotation(Auditable.class));

    assertThat(result).isEqualTo("ok");
    ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
    verify(recorder).record(captor.capture());
    AuditEvent event = captor.getValue();
    assertThat(event.action()).isEqualTo("LOGIN");
    assertThat(event.result()).isEqualTo("success");
    assertThat(event.errorMsg()).isNull();
    // 无登录态、参数有 username 字符串 → actor 取参数值、actorType=ANONYMOUS
    assertThat(event.actor()).isEqualTo("alice");
    assertThat(event.actorType()).isEqualTo("ANONYMOUS");
  }

  @Test
  @DisplayName("异常路径：捕获 result=fail 与异常消息，原异常仍向上抛出")
  void should_captureFailure_onException() throws Throwable {
    when(recorderProvider.getIfAvailable()).thenReturn(recorder);
    when(joinPoint.getSignature()).thenReturn(signature);
    when(signature.getMethod()).thenReturn(method("boom"));
    when(signature.getParameterNames()).thenReturn(new String[0]);
    when(joinPoint.getArgs()).thenReturn(new Object[0]);
    when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom!"));

    Auditable auditable = method("boom").getAnnotation(Auditable.class);
    assertThatThrownBy(() -> aspect.around(joinPoint, auditable))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom!");

    ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
    verify(recorder).record(captor.capture());
    AuditEvent event = captor.getValue();
    assertThat(event.result()).isEqualTo("fail");
    assertThat(event.errorMsg()).isEqualTo("boom!");
  }

  @Test
  @DisplayName("密码参数脱敏：顶层 password 参数值替换为 ***，不泄露明文")
  void should_maskTopLevelPasswordParam() throws Throwable {
    when(recorderProvider.getIfAvailable()).thenReturn(recorder);
    when(joinPoint.getSignature()).thenReturn(signature);
    when(signature.getMethod()).thenReturn(method("login", String.class, String.class));
    when(signature.getParameterNames()).thenReturn(new String[] {"username", "password"});
    when(joinPoint.getArgs()).thenReturn(new Object[] {"alice", "super-secret-pw"});
    when(joinPoint.proceed()).thenReturn("ok");

    aspect.around(
        joinPoint, method("login", String.class, String.class).getAnnotation(Auditable.class));

    ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
    verify(recorder).record(captor.capture());
    String params = captor.getValue().params();
    JsonNode node = objectMapper.readTree(params);
    assertThat(node.get("username").asText()).isEqualTo("alice");
    assertThat(node.get("password").asText()).isEqualTo("***");
    assertThat(params).doesNotContain("super-secret-pw");
  }

  @Test
  @DisplayName("嵌套 DTO 敏感字段脱敏：password / captchaCode getter 值替换为 ***")
  void should_maskNestedDtoSensitiveFields() throws Throwable {
    when(recorderProvider.getIfAvailable()).thenReturn(recorder);
    when(joinPoint.getSignature()).thenReturn(signature);
    when(signature.getMethod()).thenReturn(method("update", Long.class, CredentialDto.class));
    when(signature.getParameterNames()).thenReturn(new String[] {"id", "cred"});
    CredentialDto cred = new CredentialDto("bob", "nested-pw", "1234");
    when(joinPoint.getArgs()).thenReturn(new Object[] {42L, cred});
    when(joinPoint.proceed()).thenReturn("ok");

    aspect.around(
        joinPoint,
        method("update", Long.class, CredentialDto.class).getAnnotation(Auditable.class));

    ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
    verify(recorder).record(captor.capture());
    AuditEvent event = captor.getValue();
    assertThat(event.targetType()).isEqualTo("USER");
    assertThat(event.targetId()).isEqualTo("42");
    String params = event.params();
    assertThat(params).doesNotContain("nested-pw").doesNotContain("1234");
    JsonNode credNode = objectMapper.readTree(params).get("cred");
    assertThat(credNode.get("username").asText()).isEqualTo("bob");
    assertThat(credNode.get("password").asText()).isEqualTo("***");
    assertThat(credNode.get("captchaCode").asText()).isEqualTo("***");
  }

  @Test
  @DisplayName("无 AuditRecorder 实现：不抛异常，业务正常返回（优雅降级）")
  void should_degradeGracefully_when_noRecorder() throws Throwable {
    when(recorderProvider.getIfAvailable()).thenReturn(null);
    when(joinPoint.getSignature()).thenReturn(signature);
    when(signature.getMethod()).thenReturn(method("login", String.class, String.class));
    when(signature.getParameterNames()).thenReturn(new String[] {"username", "password"});
    when(joinPoint.getArgs()).thenReturn(new Object[] {"alice", "pw"});
    when(joinPoint.proceed()).thenReturn("ok");

    Object result =
        aspect.around(
            joinPoint, method("login", String.class, String.class).getAnnotation(Auditable.class));

    assertThat(result).isEqualTo("ok");
    verify(recorder, times(0)).record(any());
  }
}
