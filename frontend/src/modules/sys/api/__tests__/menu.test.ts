import { describe, it, expect, vi, beforeEach } from 'vitest'
import { menuApi } from '@/modules/sys/api/menu'
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

describe('menuApi', () => {
  beforeEach(() => vi.clearAllMocks())

  describe('tree', () => {
    it('should GET /sys/menu/tree', async () => {
      const result = { code: 0, message: 'ok', data: [] }
      mockHttp.get.mockResolvedValue(result)
      const res = await menuApi.tree()
      expect(mockHttp.get).toHaveBeenCalledWith('/sys/menu/tree')
      expect(res).toBe(result)
    })
  })

  describe('get', () => {
    it('should GET /sys/menu/:id', async () => {
      mockHttp.get.mockResolvedValue({ code: 0, message: 'ok', data: {} })
      await menuApi.get(9)
      expect(mockHttp.get).toHaveBeenCalledWith('/sys/menu/9')
    })
  })

  describe('create', () => {
    it('should POST /sys/menu with the DTO as body', async () => {
      const dto = { menuName: '系统管理', menuType: 'M', path: '/sys', sort: 1, status: 1 }
      mockHttp.post.mockResolvedValue({ code: 0, message: 'ok', data: 1 })
      await menuApi.create(dto)
      expect(mockHttp.post).toHaveBeenCalledWith('/sys/menu', dto)
    })
  })

  describe('update', () => {
    it('should PUT /sys/menu/:id with the DTO as body', async () => {
      const dto = { menuName: '系统管理', menuType: 'M' }
      mockHttp.put.mockResolvedValue({ code: 0, message: 'ok', data: null })
      await menuApi.update(9, dto)
      expect(mockHttp.put).toHaveBeenCalledWith('/sys/menu/9', dto)
    })
  })

  describe('delete', () => {
    it('should DELETE /sys/menu/:id', async () => {
      mockHttp.delete.mockResolvedValue({ code: 0, message: 'ok', data: null })
      await menuApi.delete(9)
      expect(mockHttp.delete).toHaveBeenCalledWith('/sys/menu/9')
    })
  })

  describe('error propagation', () => {
    it('should reject when http.get rejects', async () => {
      mockHttp.get.mockRejectedValue(new Error('down'))
      await expect(menuApi.tree()).rejects.toThrow('down')
    })
  })
})
