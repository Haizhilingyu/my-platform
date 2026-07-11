import { describe, it, expect, vi, beforeEach } from 'vitest'
import { auditApi } from '@/modules/sys/api/audit'
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

describe('auditApi', () => {
  beforeEach(() => vi.clearAllMocks())

  describe('list', () => {
    it('should GET /sys/audit/logs with query passed as params', async () => {
      const query = {
        actor: 'alice',
        action: 'LOGIN',
        result: 'success' as const,
        pageNum: 1,
        pageSize: 20,
      }
      const result = { code: 0, message: 'ok', data: { list: [], total: 0 } }
      mockHttp.get.mockResolvedValue(result)

      const res = await auditApi.list(query)

      expect(mockHttp.get).toHaveBeenCalledWith('/sys/audit/logs', { params: query })
      expect(res).toBe(result)
    })
  })

  describe('error propagation', () => {
    it('should reject when the underlying http call rejects', async () => {
      mockHttp.get.mockRejectedValue(new Error('network down'))
      await expect(
        auditApi.list({ actor: 'alice', action: 'LOGIN', result: 'success', pageNum: 1, pageSize: 20 })
      ).rejects.toThrow('network down')
    })
  })
})
