package com.example.common.audit;

import com.example.common.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审计切面。拦截所有标注 {@link Auditable} 的方法，在请求线程中同步采集上下文 （操作人、IP、UA、参数、结果），组装为 {@link AuditEvent} 后交给
 * {@link AuditRecorder} 异步落库。
 *
 * <p>采集本身仅做内存操作 + 一次 JSON 序列化 + 一次异步分发，不触碰数据库，保证 AOP 开销 &lt; 5ms。真正的 DB 写入由 recorder 实现在独立线程池完成。
 *
 * <p>当容器中不存在任何 {@link AuditRecorder} bean（如未引入 audit 模块）时，切面 通过 {@link ObjectProvider}
 * 优雅降级——审计采集仍执行（用于排障日志），但不落库。
 */
@Aspect
@Component
public class AuditAspect {

  private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

  /** 参数名匹配这些前缀时，值会被脱敏为 "***"。 */
  private static final Set<String> SENSITIVE_PREFIXES =
      Set.of(
          "password",
          "passwd",
          "oldpassword",
          "newpassword",
          "confirmpassword",
          "secret",
          "token",
          "accesstoken",
          "refreshtoken",
          "captchacode",
          "captcha",
          "credential",
          "apikey");

  private static final String MASK = "***";

  private final ObjectProvider<AuditRecorder> recorderProvider;
  private final ObjectMapper objectMapper;

  @Autowired
  public AuditAspect(ObjectProvider<AuditRecorder> recorderProvider, ObjectMapper objectMapper) {
    this.recorderProvider = recorderProvider;
    this.objectMapper = objectMapper;
  }

  @Around("@annotation(auditable)")
  public Object around(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
    LocalDateTime occurredAt = LocalDateTime.now();
    boolean success = true;
    String errorMsg = null;
    try {
      return joinPoint.proceed();
    } catch (Throwable ex) {
      success = false;
      errorMsg = ex.getMessage();
      throw ex;
    } finally {
      // 必须在 finally 中执行：成功/失败两种路径都要产生审计记录；且审计本身任何异常都不得影响主流程。
      try {
        dispatch(buildEvent(joinPoint, auditable, occurredAt, success, errorMsg));
      } catch (Throwable t) {
        log.warn("审计事件分发失败 action={}: {}", auditable.action(), t.getMessage());
      }
    }
  }

  private AuditEvent buildEvent(
      ProceedingJoinPoint joinPoint,
      Auditable auditable,
      LocalDateTime occurredAt,
      boolean success,
      String errorMsg) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    String[] paramNames = signature.getParameterNames();
    Object[] args = joinPoint.getArgs();

    Actor actor = resolveActor(args);
    String params = serializeParams(paramNames, args);
    Target target = resolveTarget(auditable, paramNames, args);

    RequestInfo req = extractRequest();

    return new AuditEvent(
        actor.name,
        actor.type,
        auditable.action(),
        target.type,
        target.id,
        req.ip,
        req.userAgent,
        params,
        success ? "success" : "fail",
        success ? null : errorMsg,
        occurredAt);
  }

  private void dispatch(AuditEvent event) {
    AuditRecorder recorder = recorderProvider.getIfAvailable();
    if (recorder != null) {
      recorder.record(event);
    } else {
      log.debug(
          "无 AuditRecorder 实现，审计事件仅记日志: action={} actor={} result={}",
          event.action(),
          event.actor(),
          event.result());
    }
  }

  // ---- 操作人解析：优先 CurrentUser；未登录（如登录接口）则从参数启发式提取用户名 ----

  private Actor resolveActor(Object[] args) {
    CurrentUser.UserInfo user = CurrentUser.get();
    if (user != null && user.username() != null && !user.username().isBlank()) {
      return new Actor(user.username(), "USER");
    }
    String username = extractUsernameFromArgs(args);
    if (username != null) {
      return new Actor(username, "ANONYMOUS");
    }
    return new Actor("anonymous", "ANONYMOUS");
  }

  /** 反射读取 getUsername()，避免 platform-common 编译期依赖任何业务 DTO 类型。 */
  private String extractUsernameFromArgs(Object[] args) {
    if (args == null) return null;
    for (Object arg : args) {
      if (arg == null) continue;
      if (arg instanceof String s && !s.isBlank()) {
        return s;
      }
      try {
        Method getter = arg.getClass().getMethod("getUsername");
        Object val = getter.invoke(arg);
        if (val instanceof String s && !s.isBlank()) {
          return s;
        }
      } catch (ReflectiveOperationException ignored) {
        // 该参数无 getUsername()，继续尝试下一个
      }
    }
    return null;
  }

  private Target resolveTarget(Auditable auditable, String[] paramNames, Object[] args) {
    String type = auditable.targetType();
    String id = null;
    String idParam = auditable.targetIdParam();
    if (idParam != null && !idParam.isBlank() && paramNames != null) {
      for (int i = 0; i < paramNames.length && i < args.length; i++) {
        if (idParam.equals(paramNames[i]) && args[i] != null) {
          id = String.valueOf(args[i]);
          break;
        }
      }
    }
    return new Target(type, id);
  }

  private String serializeParams(String[] paramNames, Object[] args) {
    if (args == null || args.length == 0) {
      return null;
    }
    Map<String, Object> map = new HashMap<>(args.length);
    for (int i = 0; i < args.length; i++) {
      String name = (paramNames != null && i < paramNames.length) ? paramNames[i] : ("arg" + i);
      Object value = args[i];
      if (isSensitive(name)) {
        map.put(name, MASK);
      } else if (value == null || isSimpleType(value)) {
        map.put(name, value);
      } else {
        map.put(name, maskNestedFields(value));
      }
    }
    try {
      return objectMapper.writeValueAsString(map);
    } catch (Exception ex) {
      log.debug("审计参数序列化失败: {}", ex.getMessage());
      return "{\"_serializeError\":true}";
    }
  }

  /**
   * 对嵌套对象的敏感 getter 字段做反射脱敏，覆盖 DTO 内部以对象形式传入的敏感字段 （如 {@code LoginDTO.password}）。仅反射无参 get* 方法，跳过
   * getClass。
   */
  private Map<String, Object> maskNestedFields(Object value) {
    Map<String, Object> nested = new HashMap<>();
    for (Method getter : value.getClass().getMethods()) {
      String getterName = getter.getName();
      if (!getterName.startsWith("get") || getterName.length() <= 3) continue;
      if (getter.getParameterCount() != 0) continue;
      if ("getClass".equals(getterName)) continue;
      String fieldName = decapitalize(getterName.substring(3));
      if (isSensitive(fieldName)) {
        nested.put(fieldName, MASK);
      } else {
        try {
          nested.put(fieldName, getter.invoke(value));
        } catch (ReflectiveOperationException ignored) {
          // 跳过无法读取的字段
        }
      }
    }
    return nested;
  }

  private static boolean isSensitive(String name) {
    if (name == null) return false;
    String lower = name.toLowerCase(Locale.ROOT);
    return SENSITIVE_PREFIXES.stream().anyMatch(lower::startsWith);
  }

  private static boolean isSimpleType(Object value) {
    return value instanceof CharSequence
        || value instanceof Number
        || value instanceof Boolean
        || value instanceof Character
        || value instanceof java.util.Collection
        || value instanceof Map;
  }

  private static String decapitalize(String s) {
    if (s == null || s.isEmpty()) return s;
    char[] chars = s.toCharArray();
    chars[0] = Character.toLowerCase(chars[0]);
    return new String(chars);
  }

  private RequestInfo extractRequest() {
    try {
      ServletRequestAttributes attrs =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attrs == null) {
        return new RequestInfo(null, null);
      }
      var request = attrs.getRequest();
      return new RequestInfo(resolveIp(request), request.getHeader("User-Agent"));
    } catch (Throwable t) {
      return new RequestInfo(null, null);
    }
  }

  /** 优先取 X-Forwarded-For 首段（反向代理场景），回退到 remoteAddr。 */
  private String resolveIp(jakarta.servlet.http.HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      int comma = xff.indexOf(',');
      return (comma > 0 ? xff.substring(0, comma) : xff).trim();
    }
    String real = request.getHeader("X-Real-IP");
    if (real != null && !real.isBlank()) {
      return real.trim();
    }
    return request.getRemoteAddr();
  }

  private record Actor(String name, String type) {}

  private record Target(String type, String id) {}

  private record RequestInfo(String ip, String userAgent) {}
}
