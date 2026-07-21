import { describe, it, expect, vi, beforeEach, type Mock } from 'vitest'
import { i18nApi } from '@/modules/i18n/api/i18n'
import { http } from '@/modules/sys/api/http'

/**
 * i18nApi 请求链路测试。
 *
 * 所有方法薄封装同一个 `http` axios 实例；mock 该实例即可验证：
 *  - URL / method 正确；
 *  - 查询参数、路径参数、请求体透传无误；
 *  - 文件上传走 FormData + multipart 头；
 *  - 导出走 blob responseType。
 */

vi.mock('@/modules/sys/api/http', () => ({
  http: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

const mockHttp = {
  get: http.get as unknown as Mock,
  post: http.post as unknown as Mock,
  put: http.put as unknown as Mock,
  delete: http.delete as unknown as Mock,
}

describe('i18nApi', () => {
  beforeEach(() => vi.clearAllMocks())

  describe('list', () => {
    it('should GET /i18n/messages with query as params', async () => {
      const query = { locale: 'en' as const, module: 'sys', keyLike: 'user', pageNum: 1, pageSize: 10 }
      const result = { code: 0, message: 'ok', data: { list: [], total: 0 } }
      mockHttp.get.mockResolvedValue(result)

      const res = await i18nApi.list(query)

      expect(mockHttp.get).toHaveBeenCalledWith('/i18n/messages', { params: query })
      expect(res).toBe(result)
    })
  })

  describe('fetchAll', () => {
    it('should GET /i18n/messages/all with locale param', async () => {
      mockHttp.get.mockResolvedValue({ code: 0, message: 'ok', data: { ok: 'OK' } })
      await i18nApi.fetchAll('en')
      expect(mockHttp.get).toHaveBeenCalledWith('/i18n/messages/all', { params: { locale: 'en' } })
    })
  })

  describe('update', () => {
    it('should PUT /i18n/messages/:id with value body', async () => {
      mockHttp.put.mockResolvedValue({ code: 0, message: 'ok', data: {} })
      await i18nApi.update(42, { value: '你好' })
      expect(mockHttp.put).toHaveBeenCalledWith('/i18n/messages/42', { value: '你好' })
    })
  })

  describe('importJson', () => {
    it('should POST /i18n/messages/import with import DTO', async () => {
      mockHttp.post.mockResolvedValue({ code: 0, message: 'ok', data: 3 })
      const dto = { locale: 'en' as const, items: [{ messageKey: 'ok', value: 'OK' }] }
      await i18nApi.importJson(dto)
      expect(mockHttp.post).toHaveBeenCalledWith('/i18n/messages/import', dto)
    })
  })

  describe('importXlsx', () => {
    it('should POST multipart/form-data with file + locale + xlsx format param', async () => {
      mockHttp.post.mockResolvedValue({ code: 0, message: 'ok', data: 5 })
      const file = new File(['x'], 'i18n.xlsx', {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      })

      await i18nApi.importXlsx(file, 'zh-CN')

      const [url, form, opts] = mockHttp.post.mock.calls[0]
      expect(url).toBe('/i18n/messages/import')
      expect(form).toBeInstanceOf(FormData)
      expect((form as FormData).get('file')).toBe(file)
      expect((form as FormData).get('locale')).toBe('zh-CN')
      expect(opts).toEqual({
        params: { format: 'xlsx' },
        headers: { 'Content-Type': 'multipart/form-data' },
      })
    })
  })

  describe('exportJson', () => {
    it('should GET /i18n/messages/export with json format + blob responseType', async () => {
      const blob = new Blob(['{}'], { type: 'application/json' })
      mockHttp.get.mockResolvedValue(blob)

      const res = await i18nApi.exportJson('en')

      expect(mockHttp.get).toHaveBeenCalledWith('/i18n/messages/export', {
        params: { locale: 'en', format: 'json' },
        responseType: 'blob',
      })
      expect(res).toBe(blob)
    })
  })

  describe('exportXlsx', () => {
    it('should GET /i18n/messages/export with xlsx format + blob responseType', async () => {
      mockHttp.get.mockResolvedValue(new Blob(['x']))
      await i18nApi.exportXlsx('zh-CN')
      expect(mockHttp.get).toHaveBeenCalledWith('/i18n/messages/export', {
        params: { locale: 'zh-CN', format: 'xlsx' },
        responseType: 'blob',
      })
    })
  })
})
