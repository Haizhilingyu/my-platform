export default {
  // login method labels
  passwordMethod: '账号密码',

  // form labels
  username: '用户名',
  password: '密码',
  captchaCode: '验证码',

  // placeholders
  usernamePlaceholder: '请输入用户名',
  passwordPlaceholder: '请输入密码',
  captchaPlaceholder: '请输入验证码',

  // validation error messages (fallbacks, prefer common.xxx from validation.ts)
  usernameRequired: '用户名不能为空',
  usernameTooLong: '用户名长度不能超过32',
  passwordRequired: '密码不能为空',
  passwordTooLong: '密码长度不能超过32',
  captchaTooLong: '验证码长度不能超过6',

  // error messages
  captchaLoadFailed: '验证码加载失败，请刷新重试',
  accountLocked: '账号已锁定，联系管理员',
  captchaError: '验证码错误',
  requestError: '请求参数错误',
  invalidCredentials: '用户名或密码错误',
  loginFailed: '登录失败，请稍后重试',

  // success messages
  loginSuccess: '登录成功',

  // branding / layout
  subtitle: '模块化开发平台',
  featureModules: '用户、角色、权限、通知的模块化管理',
  featureSecurity: 'JWT 认证与 OAuth2 授权服务',
  featureAudit: '全链路操作审计与实时推送',

  // button text
  login: '登录',

  // accessibility
  refreshCaptcha: '点击刷新验证码',
}