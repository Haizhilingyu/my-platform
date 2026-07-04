package com.example.common.login;

/**
 * 登录结果标记接口。
 *
 * <p>由各 {@link LoginMethodProvider} 实现返回具体的登录响应对象（如 sys 模块的 {@code LoginVO}）。
 * platform-common 不感知具体响应结构（避免反向依赖业务模块），仅以本接口作为
 * {@link LoginMethodProvider#authenticate} 的返回类型契约。
 *
 * <p>实现方通常是已有的登录响应 DTO，只需 {@code implements LoginResult} 即可，
 * 不影响其 JSON 序列化结构（Jackson 对标记接口透明）。
 */
public interface LoginResult {}
