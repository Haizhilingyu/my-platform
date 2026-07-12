export default {
  // login method labels
  passwordMethod: 'Username & Password',

  // form labels
  username: 'Username',
  password: 'Password',
  captchaCode: 'Verification Code',

  // placeholders
  usernamePlaceholder: 'Enter username',
  passwordPlaceholder: 'Enter password',
  captchaPlaceholder: 'Enter verification code',

  // validation error messages (fallbacks, prefer common.xxx from validation.ts)
  usernameRequired: 'Username cannot be empty',
  usernameTooLong: 'Username length cannot exceed 32',
  passwordRequired: 'Password cannot be empty',
  passwordTooLong: 'Password length cannot exceed 32',
  captchaTooLong: 'Verification code length cannot exceed 6',

  // error messages
  captchaLoadFailed: 'Failed to load verification code, please refresh and try again',
  accountLocked: 'Account locked, please contact administrator',
  captchaError: 'Incorrect verification code',
  requestError: 'Invalid request parameters',
  invalidCredentials: 'Invalid username or password',
  loginFailed: 'Login failed, please try again later',

  // success messages
  loginSuccess: 'Login successful',

  // branding / layout
  subtitle: 'Modular Development Platform',
  featureModules: 'Modular management of users, roles, permissions, and notifications',
  featureSecurity: 'JWT authentication and OAuth2 authorization service',
  featureAudit: 'Full-chain operational audit and real-time push notifications',

  // button text
  login: 'Sign In',

  // accessibility
  refreshCaptcha: 'Click to refresh verification code',
}