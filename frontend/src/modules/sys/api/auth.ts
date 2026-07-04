import { http } from './http'
import type {
  Result,
  LoginVO,
  UserVO,
  MenuTreeNode,
  LoginRequest,
  LoginMethodDescriptor,
  CaptchaResult,
} from './types'

export const authApi = {
  login(data: LoginRequest): Promise<Result<LoginVO>> {
    return http.post('/sys/auth/login', data)
  },

  getLoginMethods(): Promise<Result<LoginMethodDescriptor[]>> {
    return http.get('/sys/auth/login-methods')
  },

  getCaptcha(): Promise<Result<CaptchaResult>> {
    return http.get('/sys/auth/captcha')
  },

  getMe(): Promise<Result<UserVO>> {
    return http.get('/sys/auth/me')
  },

  getPermissions(): Promise<Result<string[]>> {
    return http.get('/sys/auth/permissions')
  },

  getMenus(): Promise<Result<MenuTreeNode[]>> {
    return http.get('/sys/auth/menus')
  },
}
