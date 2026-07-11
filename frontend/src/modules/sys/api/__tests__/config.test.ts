import { describe, it, expect, vi, beforeEach } from 'vitest'
import { configApi } from '@/modules/sys/api/config'
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

describe('configApi', () => {
  beforeEach(() => vi.clearAllMocks())

  describe('list', () => {
    it('should GET /sys/config with category passed as a param when provided', async () => {
      mockHttp.get.mockResolvedValue({ code: 0, message: 'ok', data: [] })
      await configApi.list('security')
      expect(mockHttp.get).toHaveBeenCalledWith('/sys/config', { params: { category: 'security' } })
    })

    it('should pass category=undefined in params when omitted', async () => {
      mockHttp.get.mockResolvedValue({ code: 0, message: 'ok', data: [] })
      await configApi.list()
      expect(mockHttp.get).toHaveBeenCalledWith('/sys/config', { params: { category: undefined } })
    })
  })

  describe('getByKey', () => {
    it('should GET /sys/config/:key', async () => {
      mockHttp.get.mockResolvedValue({ code: 0, message: 'ok', data: {} })
      await configApi.getByKey('sys.title')
      expect(mockHttp.get).toHaveBeenCalledWith('/sys/config/sys.title')
    })
  })

  describe('create', () => {
    it('should POST /sys/config with the DTO as body', async () => {
      const dto = { configKey: 'a.b', configValue: 'v', configType: 'string', category: 'general' }
      mockHttp.post.mockResolvedValue({ code: 0, message: 'ok', data: 1 })
      await configApi.create(dto)
      expect(mockHttp.post).toHaveBeenCalledWith('/sys/config', dto)
    })
  })

  describe('update', () => {
    it('should PUT /sys/config/:id with the DTO as body', async () => {
      const dto = { configKey: 'a.b', configValue: 'v2' }
      mockHttp.put.mockResolvedValue({ code: 0, message: 'ok', data: null })
      await configApi.update(2, dto)
      expect(mockHttp.put).toHaveBeenCalledWith('/sys/config/2', dto)
    })
  })

  describe('batchUpdate', () => {
    it('should PUT /sys/config/batch with the config array as body', async () => {
      const configs = [
        { id: 1, configKey: 'a', configValue: '1' },
        { id: 2, configKey: 'b', configValue: '2' },
      ]
      mockHttp.put.mockResolvedValue({ code: 0, message: 'ok', data: null })
      await configApi.batchUpdate(configs)
      expect(mockHttp.put).toHaveBeenCalledWith('/sys/config/batch', configs)
    })
  })

  describe('error propagation', () => {
    it('should reject when http.get rejects', async () => {
      mockHttp.get.mockRejectedValue(new Error('err'))
      await expect(configApi.getByKey('x')).rejects.toThrow('err')
    })
  })
})
