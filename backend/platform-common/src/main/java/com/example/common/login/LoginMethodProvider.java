package com.example.common.login;

/**
 * 登录方式提供者 SPI。
 *
 * <p>每种登录方式（密码、LDAP、SSO 等）提供一个实现，Spring 自动收集到 {@link LoginMethodRegistry}。业务模块（如
 * sys）提供实现，platform-common 只定义契约——依赖倒置，避免 common 反向依赖业务模块。
 *
 * <p>实现要点：
 *
 * <ul>
 *   <li>标注 {@code @Component}（或等价 Spring 构造型注解）使其被容器扫描。
 *   <li>{@code getOrder()} 返回唯一排序权重（建议 10/20/100 间隔，便于插入新方式）。
 *   <li>{@code isEnabled()} 返回 false 时，该方式既不出现在 /login-methods 列表， 也无法通过 login 接口调用（registry 的
 *       getProvider 返回 null）。
 * </ul>
 */
public interface LoginMethodProvider {

  /** 登录方式标识，全局唯一，如 "password"、"ldap"。 */
  String getMethod();

  /**
   * 执行认证。
   *
   * @param request 登录请求（含 method/username/password/attributes）
   * @return 登录结果（如 LoginVO）。认证失败时应抛出 {@code BizException}。
   */
  LoginResult authenticate(LoginRequest request);

  /** 该方式是否启用。返回 false 的提供者不对外暴露且不可调用。 */
  boolean isEnabled();

  /** 排序权重，升序。前端 Tab 按此值从小到大排列。 */
  int getOrder();

  /** 返回前端渲染所需的描述信息。 */
  LoginMethodDescriptor describe();
}
