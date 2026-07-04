package com.example.common.exception;

/**
 * 账号锁定异常。登录失败次数超过阈值时抛出，由 {@link GlobalExceptionHandler} 统一映射为 HTTP 423 Locked。
 *
 * <p>语义：账号因连续登录失败被临时锁定，需管理员解锁或等待锁定窗口过期。与普通 {@link
 * BizException}(401 凭证错误) 区分，便于前端精确提示「账号已锁定，请联系管理员」。
 *
 * <p>状态码 423 来源于 WebDAV 扩展（RFC 4918），语义为「资源被锁定」，契合账号锁定场景。 Spring
 * {@link org.springframework.http.HttpStatus} 已包含 {@code LOCKED(423, "Locked")}。
 */
public class AccountLockedException extends BizException {

    /** 锁定 HTTP 状态码。 */
    public static final int LOCKED_CODE = 423;

    public AccountLockedException(String message) {
        super(LOCKED_CODE, message);
    }

    /** 默认提示文案。 */
    public static AccountLockedException defaultMessage() {
        return new AccountLockedException("账号已锁定，请联系管理员或稍后重试");
    }
}
