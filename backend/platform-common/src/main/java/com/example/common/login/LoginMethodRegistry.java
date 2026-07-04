package com.example.common.login;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 登录方式注册表。
 *
 * <p>Spring 启动时通过构造器注入收集所有 {@link LoginMethodProvider} bean， 按 {@link
 * LoginMethodProvider#getOrder()} 升序排序，并构建 method→provider 索引。
 *
 * <p>查询方法（{@link #getEnabledMethods()} / {@link #getProvider(String)} / {@link
 * #getEnabledMethodNames()}） 均自动过滤 {@code isEnabled()==false} 的提供者，确保禁用的方式既不暴露也不可路由。
 *
 * <p>本类线程安全：构造完成后内部列表与索引均不可变，运行时只读。
 */
@Component
public class LoginMethodRegistry {

  private final List<LoginMethodProvider> sortedProviders;
  private final Map<String, LoginMethodProvider> providerByMethod;

  public LoginMethodRegistry(List<LoginMethodProvider> providers) {
    this.sortedProviders =
        providers.stream().sorted(Comparator.comparingInt(LoginMethodProvider::getOrder)).toList();
    this.providerByMethod =
        sortedProviders.stream()
            .collect(
                Collectors.toMap(
                    LoginMethodProvider::getMethod,
                    Function.identity(),
                    (a, b) -> a,
                    java.util.LinkedHashMap::new));
  }

  /** 返回所有已启用方式的描述符列表，按 order 升序。 */
  public List<LoginMethodDescriptor> getEnabledMethods() {
    return sortedProviders.stream()
        .filter(LoginMethodProvider::isEnabled)
        .map(LoginMethodProvider::describe)
        .toList();
  }

  /**
   * 按方法标识查找已启用的提供者。
   *
   * @param method 登录方式标识（如 "password"）
   * @return 已启用的提供者；method 为 null / 未知 / 已禁用时返回 null
   */
  public LoginMethodProvider getProvider(String method) {
    if (method == null) {
      return null;
    }
    LoginMethodProvider provider = providerByMethod.get(method);
    return (provider != null && provider.isEnabled()) ? provider : null;
  }

  /** 返回所有已启用方式的方法标识集合，按 order 升序（LinkedHashSet 保序）。 */
  public Set<String> getEnabledMethodNames() {
    return sortedProviders.stream()
        .filter(LoginMethodProvider::isEnabled)
        .map(LoginMethodProvider::getMethod)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
