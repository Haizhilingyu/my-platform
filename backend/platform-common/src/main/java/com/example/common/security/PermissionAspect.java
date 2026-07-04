package com.example.common.security;

import com.example.common.exception.ForbiddenException;
import java.util.Arrays;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/** 权限校验切面。拦截所有标注了 {@link RequiresPermission} 的方法。 */
@Aspect
@Component
public class PermissionAspect {

  @Around("@annotation(requiresPermission)")
  public Object checkPermission(
      ProceedingJoinPoint joinPoint, RequiresPermission requiresPermission) throws Throwable {
    String[] required = requiresPermission.value();
    if (required.length == 0) {
      return joinPoint.proceed();
    }

    boolean permitted;
    if (requiresPermission.logical() == Logical.AND) {
      permitted = Arrays.stream(required).allMatch(CurrentUser::hasPermission);
    } else {
      permitted = Arrays.stream(required).anyMatch(CurrentUser::hasPermission);
    }

    if (!permitted) {
      throw new ForbiddenException("无权限: " + String.join(", ", required));
    }

    return joinPoint.proceed();
  }
}
