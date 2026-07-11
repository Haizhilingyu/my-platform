import { describe, it, expect, vi, beforeEach } from 'vitest'
import { unitApi } from '@/modules/sys/api/unit'
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

describe('unitApi', () => {
  beforeEach(() => vi.clearAllMocks())

  describe('tree', () => {
    it('should GET /sys/unit/tree', async () => {
      const result = { code: 0, message: 'ok', data: [] }
      mockHttp.get.mockResolvedValue(result)
      const res = await unitApi.tree()
      expect(mockHttp.get).toHaveBeenCalledWith('/sys/unit/tree')
      expect(res).toBe(result)
    })
  })

  describe('get', () => {
    it('should GET /sys/unit/:id', async () => {
      mockHttp.get.mockResolvedValue({ code: 0, message: 'ok', data: {} })
      await unitApi.get(5)
      expect(mockHttp.get).toHaveBeenCalledWith('/sys/unit/5')
    })
  })

  describe('create', () => {
    it('should POST /sys/unit with the DTO as body', async () => {
      const dto = { unitCode: 'HQ', unitName: '总部', sort: 1, status: 1 }
      mockHttp.post.mockResolvedValue({ code: 0, message: 'ok', data: 1 })
      await unitApi.create(dto)
      expect(mockHttp.post).toHaveBeenCalledWith('/sys/unit', dto)
    })
  })

  describe('update', () => {
    it('should PUT /sys/unit/:id with the DTO as body', async () => {
      const dto = { unitCode: 'HQ', unitName: '总部' }
      mockHttp.put.mockResolvedValue({ code: 0, message: 'ok', data: null })
      await unitApi.update(5, dto)
      expect(mockHttp.put).toHaveBeenCalledWith('/sys/unit/5', dto)
    })
  })

  describe('delete', () => {
    it('should DELETE /sys/unit/:id', async () => {
      mockHttp.delete.mockResolvedValue({ code: 0, message: 'ok', data: null })
      await unitApi.delete(5)
      expect(mockHttp.delete).toHaveBeenCalledWith('/sys/unit/5')
    })
  })

  describe('error propagation', () => {
    it('should reject when http.put rejects', async () => {
      mockHttp.put.mockRejectedValue(new Error('conflict'))
      await expect(unitApi.update(5, { unitCode: 'X', unitName: 'X' })).rejects.toThrow('conflict')
    })
  })
})
