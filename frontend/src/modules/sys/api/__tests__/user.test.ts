import { describe, it, expect, vi, beforeEach } from 'vitest'
import { userApi } from '@/modules/sys/api/user'
import { http } from '@/modules/sys/api/http'

// 所有 API 模块都薄封装同一个 `http` axios 实例；mock 该实例即可验证请求链路。
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

describe('userApi', () => {
  beforeEach(() => vi.clearAllMocks())

  describe('list', () => {
    it('should GET /sys/user with query passed as params', async () => {
      const query = { keyword: 'alice', unitId: 2, status: 1, pageNum: 1, pageSize: 10 }
      const result = { code: 0, message: 'ok', data: { list: [], total: 0 } }
      mockHttp.get.mockResolvedValue(result)

      const res = await userApi.list(query)

      expect(mockHttp.get).toHaveBeenCalledWith('/sys/user', { params: query })
      expect(res).toBe(result)
    })
  })

  describe('get', () => {
    it('should GET /sys/user/:id', async () => {
      mockHttp.get.mockResolvedValue({ code: 0, message: 'ok', data: {} })
      await userApi.get(7)
      expect(mockHttp.get).toHaveBeenCalledWith('/sys/user/7')
    })
  })

  describe('create', () => {
    it('should POST /sys/user with the DTO as body', async () => {
      const dto = {
        username: 'alice',
        password: 'secret',
        realName: 'Alice',
        email: 'a@x.com',
        phone: '13800138000',
        unitId: 1,
        roleIds: [1, 2],
      }
      mockHttp.post.mockResolvedValue({ code: 0, message: 'ok', data: 1 })
      await userApi.create(dto)
      expect(mockHttp.post).toHaveBeenCalledWith('/sys/user', dto)
    })
  })

  describe('update', () => {
    it('should PUT /sys/user/:id with the DTO as body', async () => {
      const dto = { realName: 'Alice', status: 1, remark: 'x' }
      mockHttp.put.mockResolvedValue({ code: 0, message: 'ok', data: null })
      await userApi.update(7, dto)
      expect(mockHttp.put).toHaveBeenCalledWith('/sys/user/7', dto)
    })
  })

  describe('delete', () => {
    it('should DELETE /sys/user/:id', async () => {
      mockHttp.delete.mockResolvedValue({ code: 0, message: 'ok', data: null })
      await userApi.delete(7)
      expect(mockHttp.delete).toHaveBeenCalledWith('/sys/user/7')
    })
  })

  describe('assignRoles', () => {
    it('should POST /sys/user/:id/roles with roleIds as body', async () => {
      mockHttp.post.mockResolvedValue({ code: 0, message: 'ok', data: null })
      await userApi.assignRoles(7, [1, 2, 3])
      expect(mockHttp.post).toHaveBeenCalledWith('/sys/user/7/roles', [1, 2, 3])
    })
  })

  describe('getUserRoles', () => {
    it('should GET /sys/user/:id/roles', async () => {
      mockHttp.get.mockResolvedValue({ code: 0, message: 'ok', data: [] })
      await userApi.getUserRoles(7)
      expect(mockHttp.get).toHaveBeenCalledWith('/sys/user/7/roles')
    })
  })

  describe('resetPassword', () => {
    it('should POST /sys/user/:id/reset-password with newPassword as param', async () => {
      mockHttp.post.mockResolvedValue({ code: 0, message: 'ok', data: null })
      await userApi.resetPassword(7, 'newpass')
      expect(mockHttp.post).toHaveBeenCalledWith('/sys/user/7/reset-password', null, {
        params: { newPassword: 'newpass' },
      })
    })
  })

  describe('unlock', () => {
    it('should POST /sys/user/:id/unlock', async () => {
      mockHttp.post.mockResolvedValue({ code: 0, message: 'ok', data: null })
      await userApi.unlock(7)
      expect(mockHttp.post).toHaveBeenCalledWith('/sys/user/7/unlock')
    })
  })

  describe('error propagation', () => {
    it('should reject when the underlying http call rejects', async () => {
      mockHttp.get.mockRejectedValue(new Error('network down'))
      await expect(userApi.get(1)).rejects.toThrow('network down')
    })
  })
})
