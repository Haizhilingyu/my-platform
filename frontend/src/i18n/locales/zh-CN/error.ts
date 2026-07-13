export default {
  // Auth errors
  'error.auth.bad.credentials': '用户名或密码错误',
  'error.user.disabled': '用户已被禁用',
  'error.auth.captcha.required': '请输入验证码',
  'error.auth.captcha.invalid': '验证码错误或已过期',
  'error.auth.not.login': '未登录',
  'error.auth.method.unsupported': '不支持的登录方式',

  // User errors
  'user.username.exists': '用户名已存在',
  'user.delete.forbidden': '无权删除数据权限范围外的用户',

  // Role errors
  'role.code.exists': '角色编码已存在',

  // Menu errors
  'menu.parent.self': '上级菜单不能是自己',
  'menu.has.children': '存在子菜单，无法删除',

  // Config errors
  'config.key.exists': '配置键已存在',

  // Unit errors
  'unit.code.exists': '单位编码已存在',
  'unit.parent.self': '上级单位不能是自己',
  'unit.has.children': '存在子单位，无法删除',

  // Session errors
  'session.not.found': '会话不存在或已过期',
  'session.revoke.forbidden': '无权撤销他人会话',

  // Permission/scope errors
  'error.permission.denied': '无权限',
  'error.scope.no.token': '未授权：缺少有效的 OAuth2 access_token',
  'error.scope.not.oauth2': '非 OAuth2 令牌',
  'error.scope.missing': '缺少 OAuth2 scope',

  // Generic errors
  'error.access.denied': '无权限访问',
  'error.system': '系统内部错误',

  // LDAP errors
  'ldap.empty.credentials': '用户名和密码不能为空',
  'ldap.auth.failed': 'LDAP 认证失败',
  'ldap.service.unavailable': 'LDAP 服务不可用',

  // Account
  'error.account.locked': '账号已锁定，请联系管理员或稍后重试',
}
