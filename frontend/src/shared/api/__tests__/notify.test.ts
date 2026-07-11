import { describe, it, expect, vi, beforeEach } from 'vitest'
import { notifyApi } from '@/shared/api/notify'
import { http } from '@/modules/sys/api/http'

// 共享 API 通过别名 '@/modules/sys/api/http' 复用同一个 http 实例。
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

describe('notifyApi', () => {
  beforeEach(() => vi.clearAllMocks())

  describe('inbox', () => {
    it('should GET /sys/notify/inbox with query as params', async () => {
      const query = { level: 'URGENT', readStatus: false, keyword: 'x', pageNum: 1, pageSize: 10 }
      const result = { code: 0, message: 'ok', data: { list: [], total: 0 } }
      mockHttp.get.mockResolvedValue(result)
      const res = await notifyApi.inbox(query)
      expect(mockHttp.get).toHaveBeenCalledWith('/sys/notify/inbox', { params: query })
      expect(res).toBe(result)
    })
  })

  describe('markRead', () => {
    it('should PUT /sys/notify/inbox/:id/read', async () => {
      mockHttp.put.mockResolvedValue({ code: 0, message: 'ok', data: null })
      await notifyApi.markRead(11)
      expect(mockHttp.put).toHaveBeenCalledWith('/sys/notify/inbox/11/read')
    })
  })

  describe('batchMarkRead', () => {
    it('should PUT /sys/notify/inbox/batch-read with { ids } as body', async () => {
      mockHttp.put.mockResolvedValue({ code: 0, message: 'ok', data: null })
      await notifyApi.batchMarkRead([1, 2, 3])
      expect(mockHttp.put).toHaveBeenCalledWith('/sys/notify/inbox/batch-read', { ids: [1, 2, 3] })
    })
  })

  describe('unreadCount', () => {
    it('should GET /sys/notify/inbox/unread-count', async () => {
      const result = { code: 0, message: 'ok', data: 0 }
      mockHttp.get.mockResolvedValue(result)
      const res = await notifyApi.unreadCount()
      expect(mockHttp.get).toHaveBeenCalledWith('/sys/notify/inbox/unread-count')
      expect(res).toBe(result)
    })
  })

  describe('error propagation', () => {
    it('should reject when http.get rejects', async () => {
      mockHttp.get.mockRejectedValue(new Error('down'))
      await expect(notifyApi.unreadCount()).rejects.toThrow('down')
    })
  })
})
