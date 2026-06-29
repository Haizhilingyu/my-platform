import { http } from './http'
import type { Result, LoginVO, UserVO, MenuTreeNode } from './types'

export const authApi = {
  login(data: { username: string; password: string }): Promise<Result<LoginVO>> {
    return http.post('/sys/auth/login', data)
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
