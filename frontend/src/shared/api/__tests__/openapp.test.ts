import { describe, it, expect, vi, beforeEach } from 'vitest'
import { openAppApi } from '@/shared/api/openapp'
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

describe('openAppApi', () => {
  beforeEach(() => vi.clearAllMocks())

  describe('list', () => {
    it('should GET /sys/openapp/clients with query as params', async () => {
      const query = { keyword: 'app', enabled: true, pageNum: 1, pageSize: 10 }
      const result = { code: 0, message: 'ok', data: { list: [], total: 0 } }
      mockHttp.get.mockResolvedValue(result)
      const res = await openAppApi.list(query)
      expect(mockHttp.get).toHaveBeenCalledWith('/sys/openapp/clients', { params: query })
      expect(res).toBe(result)
    })
  })

  describe('get', () => {
    it('should GET /sys/openapp/clients/:id', async () => {
      mockHttp.get.mockResolvedValue({ code: 0, message: 'ok', data: {} })
      await openAppApi.get(4)
      expect(mockHttp.get).toHaveBeenCalledWith('/sys/openapp/clients/4')
    })
  })

  describe('create', () => {
    it('should POST /sys/openapp/clients with the DTO as body', async () => {
      const dto = {
        clientName: 'My App',
        redirectUris: ['https://a/cb'],
        postLogoutRedirectUris: ['https://a'],
        scopes: ['openid'],
        grantTypes: ['authorization_code'],
      }
      mockHttp.post.mockResolvedValue({ code: 0, message: 'ok', data: { id: 1, clientId: 'c', clientSecret: 's' } })
      await openAppApi.create(dto)
      expect(mockHttp.post).toHaveBeenCalledWith('/sys/openapp/clients', dto)
    })
  })

  describe('update', () => {
    it('should PUT /sys/openapp/clients/:id with the DTO as body', async () => {
      const dto = { clientName: 'My App', redirectUris: [], postLogoutRedirectUris: [], scopes: [], grantTypes: [], enabled: false }
      mockHttp.put.mockResolvedValue({ code: 0, message: 'ok', data: null })
      await openAppApi.update(4, dto)
      expect(mockHttp.put).toHaveBeenCalledWith('/sys/openapp/clients/4', dto)
    })
  })

  describe('delete', () => {
    it('should DELETE /sys/openapp/clients/:id', async () => {
      mockHttp.delete.mockResolvedValue({ code: 0, message: 'ok', data: null })
      await openAppApi.delete(4)
      expect(mockHttp.delete).toHaveBeenCalledWith('/sys/openapp/clients/4')
    })
  })

  describe('resetSecret', () => {
    it('should POST /sys/openapp/clients/:id/reset-secret', async () => {
      mockHttp.post.mockResolvedValue({ code: 0, message: 'ok', data: { id: 4, clientId: 'c', clientSecret: 's2' } })
      await openAppApi.resetSecret(4)
      expect(mockHttp.post).toHaveBeenCalledWith('/sys/openapp/clients/4/reset-secret')
    })
  })

  describe('error propagation', () => {
    it('should reject when http.post rejects', async () => {
      mockHttp.post.mockRejectedValue(new Error('fail'))
      await expect(openAppApi.resetSecret(4)).rejects.toThrow('fail')
    })
  })
})
