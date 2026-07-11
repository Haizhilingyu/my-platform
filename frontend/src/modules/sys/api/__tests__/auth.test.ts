import { describe, it, expect, vi, beforeEach } from 'vitest'
import { authApi } from '@/modules/sys/api/auth'
import { http } from '@/modules/sys/api/http'

vi.mock('@/modules/sys/api/http', () => ({
  http: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

type MockFn = ReturnType<typeof vi.fn>
const mockHttp = http as unknown as {
  get: MockFn
  post: MockFn
  put: MockFn
  delete: MockFn
}

describe('authApi', () => {
  beforeEach(() => vi.clearAllMocks())

  describe('login', () => {
    it('should POST /sys/auth/login with the credentials as body', async () => {
      const creds = { username: 'admin', password: 'secret', captcha: 'abcd' }
      const result = { code: 0, message: 'ok', data: { token: 't', user: {}, menus: [] } }
      mockHttp.post.mockResolvedValue(result)
      const res = await authApi.login(creds)
      expect(mockHttp.post).toHaveBeenCalledWith('/sys/auth/login', creds)
      expect(res).toBe(result)
    })
  })

  describe('getLoginMethods', () => {
    it('should GET /sys/auth/login-methods', async () => {
      mockHttp.get.mockResolvedValue({ code: 0, message: 'ok', data: [] })
      await authApi.getLoginMethods()
      expect(mockHttp.get).toHaveBeenCalledWith('/sys/auth/login-methods')
    })
  })

  describe('getCaptcha', () => {
    it('should GET /sys/auth/captcha', async () => {
      mockHttp.get.mockResolvedValue({ code: 0, message: 'ok', data: { captchaId: '1', image: 'base64' } })
      await authApi.getCaptcha()
      expect(mockHttp.get).toHaveBeenCalledWith('/sys/auth/captcha')
    })
  })

  describe('getMe', () => {
    it('should GET /sys/auth/me', async () => {
      mockHttp.get.mockResolvedValue({ code: 0, message: 'ok', data: {} })
      await authApi.getMe()
      expect(mockHttp.get).toHaveBeenCalledWith('/sys/auth/me')
    })
  })

  describe('getPermissions', () => {
    it('should GET /sys/auth/permissions', async () => {
      mockHttp.get.mockResolvedValue({ code: 0, message: 'ok', data: ['sys:user:list'] })
      await authApi.getPermissions()
      expect(mockHttp.get).toHaveBeenCalledWith('/sys/auth/permissions')
    })
  })

  describe('getMenus', () => {
    it('should GET /sys/auth/menus', async () => {
      mockHttp.get.mockResolvedValue({ code: 0, message: 'ok', data: [] })
      await authApi.getMenus()
      expect(mockHttp.get).toHaveBeenCalledWith('/sys/auth/menus')
    })
  })

  describe('error propagation', () => {
    it('should reject when http.post rejects', async () => {
      mockHttp.post.mockRejectedValue(new Error('bad creds'))
      await expect(authApi.login({ username: 'x', password: 'y' })).rejects.toThrow('bad creds')
    })
  })
})
