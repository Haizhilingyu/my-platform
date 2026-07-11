import { describe, it, expect, vi, beforeEach } from 'vitest'
import { sessionApi } from '@/modules/sys/api/session'
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

describe('sessionApi', () => {
  beforeEach(() => vi.clearAllMocks())

  describe('mySessions', () => {
    it('should GET /sys/auth/sessions', async () => {
      const result = { code: 0, message: 'ok', data: [] }
      mockHttp.get.mockResolvedValue(result)

      const res = await sessionApi.mySessions()

      expect(mockHttp.get).toHaveBeenCalledWith('/sys/auth/sessions')
      expect(res).toBe(result)
    })
  })

  describe('revokeMySession', () => {
    it('should POST /sys/auth/sessions/:jti/revoke', async () => {
      mockHttp.post.mockResolvedValue({ code: 0, message: 'ok', data: null })

      await sessionApi.revokeMySession('session-abc')

      expect(mockHttp.post).toHaveBeenCalledWith('/sys/auth/sessions/session-abc/revoke')
    })
  })

  describe('userSessions', () => {
    it('should GET /sys/user/:userId/sessions', async () => {
      const result = { code: 0, message: 'ok', data: [] }
      mockHttp.get.mockResolvedValue(result)

      await sessionApi.userSessions(42)

      expect(mockHttp.get).toHaveBeenCalledWith('/sys/user/42/sessions')
      expect(await sessionApi.userSessions(42)).toBe(result)
    })
  })

  describe('revokeUserSession', () => {
    it('should POST /sys/user/:userId/sessions/:jti/revoke', async () => {
      mockHttp.post.mockResolvedValue({ code: 0, message: 'ok', data: null })

      await sessionApi.revokeUserSession(42, 'session-xyz')

      expect(mockHttp.post).toHaveBeenCalledWith('/sys/user/42/sessions/session-xyz/revoke')
    })
  })

  describe('error propagation', () => {
    it('should reject when the underlying http call rejects', async () => {
      mockHttp.get.mockRejectedValue(new Error('network down'))
      await expect(sessionApi.mySessions()).rejects.toThrow('network down')
    })
  })
})
