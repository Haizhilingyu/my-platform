import { describe, it, expect, vi, beforeEach } from 'vitest'
import { roleApi } from '@/modules/sys/api/role'
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

describe('roleApi', () => {
  beforeEach(() => vi.clearAllMocks())

  describe('list', () => {
    it('should GET /sys/role', async () => {
      mockHttp.get.mockResolvedValue({ code: 0, message: 'ok', data: [] })
      await roleApi.list()
      expect(mockHttp.get).toHaveBeenCalledWith('/sys/role')
    })
  })

  describe('get', () => {
    it('should GET /sys/role/:id', async () => {
      mockHttp.get.mockResolvedValue({ code: 0, message: 'ok', data: {} })
      await roleApi.get(3)
      expect(mockHttp.get).toHaveBeenCalledWith('/sys/role/3')
    })
  })

  describe('create', () => {
    it('should POST /sys/role with the DTO as body', async () => {
      const dto = { roleCode: 'editor', roleName: '编辑', dataScope: 'ALL', status: 1 }
      mockHttp.post.mockResolvedValue({ code: 0, message: 'ok', data: 1 })
      await roleApi.create(dto)
      expect(mockHttp.post).toHaveBeenCalledWith('/sys/role', dto)
    })
  })

  describe('update', () => {
    it('should PUT /sys/role/:id with the DTO as body', async () => {
      const dto = { roleCode: 'editor', roleName: '编辑' }
      mockHttp.put.mockResolvedValue({ code: 0, message: 'ok', data: null })
      await roleApi.update(3, dto)
      expect(mockHttp.put).toHaveBeenCalledWith('/sys/role/3', dto)
    })
  })

  describe('delete', () => {
    it('should DELETE /sys/role/:id', async () => {
      mockHttp.delete.mockResolvedValue({ code: 0, message: 'ok', data: null })
      await roleApi.delete(3)
      expect(mockHttp.delete).toHaveBeenCalledWith('/sys/role/3')
    })
  })

  describe('assignMenus', () => {
    it('should POST /sys/role/:id/menus with menuIds as body', async () => {
      mockHttp.post.mockResolvedValue({ code: 0, message: 'ok', data: null })
      await roleApi.assignMenus(3, [1, 2])
      expect(mockHttp.post).toHaveBeenCalledWith('/sys/role/3/menus', [1, 2])
    })
  })

  describe('getRoleMenus', () => {
    it('should GET /sys/role/:id/menus', async () => {
      mockHttp.get.mockResolvedValue({ code: 0, message: 'ok', data: [] })
      await roleApi.getRoleMenus(3)
      expect(mockHttp.get).toHaveBeenCalledWith('/sys/role/3/menus')
    })
  })

  describe('saveCustomUnits', () => {
    it('should PUT /sys/role/:id/data-scope with unitIds as body', async () => {
      mockHttp.put.mockResolvedValue({ code: 0, message: 'ok', data: null })
      await roleApi.saveCustomUnits(3, [4, 5])
      expect(mockHttp.put).toHaveBeenCalledWith('/sys/role/3/data-scope', [4, 5])
    })
  })

  describe('getCustomUnits', () => {
    it('should GET /sys/role/:id/data-scope', async () => {
      mockHttp.get.mockResolvedValue({ code: 0, message: 'ok', data: [] })
      await roleApi.getCustomUnits(3)
      expect(mockHttp.get).toHaveBeenCalledWith('/sys/role/3/data-scope')
    })
  })

  describe('error propagation', () => {
    it('should reject when http.post rejects', async () => {
      mockHttp.post.mockRejectedValue(new Error('boom'))
      await expect(roleApi.create({ roleCode: 'x', roleName: 'X' })).rejects.toThrow('boom')
    })
  })
})
