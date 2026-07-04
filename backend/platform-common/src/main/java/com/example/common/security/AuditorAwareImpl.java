package com.example.common.security;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * JPA 审计填充器：从 {@link CurrentUser} 读取当前用户 ID（字符串化）填入 {@code created_by}/{@code updated_by}。
 *
 * <p>与 {@link com.example.common.datapolicy.DataScopeSpecification} 的 SELF 范围对齐—— 该范围按 {@code
 * created_by = userId.toString()} 过滤，因此审计列必须存用户 ID 而非用户名。 无当前用户（如系统/迁移线程）时返回 empty，Spring Data
 * 审计将保留原值或置 null。
 */
@Component
public class AuditorAwareImpl implements AuditorAware<String> {

  @Override
  @NonNull
  public Optional<String> getCurrentAuditor() {
    Long userId = CurrentUser.getUserId();
    return userId != null ? Optional.of(String.valueOf(userId)) : Optional.empty();
  }
}
