export default {
  'error.auth.bad.credentials': 'Invalid username or password',
  'error.user.disabled': 'User has been disabled',
  'error.auth.captcha.required': 'Captcha is required',
  'error.auth.captcha.invalid': 'Invalid or expired captcha',
  'error.auth.not.login': 'Not logged in',
  'error.auth.method.unsupported': 'Unsupported login method',

  'user.username.exists': 'Username already exists',
  'user.delete.forbidden': 'Forbidden: cannot delete users outside your data scope',

  'role.code.exists': 'Role code already exists',

  'menu.parent.self': 'Parent menu cannot be itself',
  'menu.has.children': 'Has child menus, cannot delete',

  'config.key.exists': 'Config key already exists',

  'unit.code.exists': 'Unit code already exists',
  'unit.parent.self': 'Parent unit cannot be itself',
  'unit.has.children': 'Has child units, cannot delete',

  'session.not.found': 'Session not found or expired',
  'session.revoke.forbidden': 'Forbidden: cannot revoke other users\' sessions',

  'error.permission.denied': 'Permission denied',
  'error.scope.no.token': 'Unauthorized: missing valid OAuth2 access_token',
  'error.scope.not.oauth2': 'Not an OAuth2 token',
  'error.scope.missing': 'Missing OAuth2 scope',

  'error.access.denied': 'Access denied',
  'error.system': 'Internal server error',

  'ldap.empty.credentials': 'Username and password must not be empty',
  'ldap.auth.failed': 'LDAP authentication failed',
  'ldap.service.unavailable': 'LDAP service unavailable',

  'error.account.locked': 'Account is locked, please contact the administrator or try again later',
}
