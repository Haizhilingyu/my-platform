import axios from 'axios'
import { resetOverlayCache, mergeBackendMessages, SUPPORTED_LOCALES } from '@/i18n'

/**
 * i18n 模块纯函数与覆盖缓存行为测试。
 *
 * mergeBackendMessages 的网络分支由 stores/auth 的 mock 覆盖（见 stores/auth.test.ts）；
 * 此处聚焦：
 *  - SUPPORTED_LOCALES 静态导出正确；
 *  - resetOverlayCache 幂等且不抛错；
 *  - mergeBackendMessages 在无 token 时短路返回，不触发网络请求。
 */

describe('i18n 模块', () => {
  describe('SUPPORTED_LOCALES', () => {
    it('应包含 zh-CN 与 en 两个选项', () => {
      const keys = SUPPORTED_LOCALES.map((l) => l.value)
      expect(keys).toEqual(['zh-CN', 'en'])
    })
  })

  describe('resetOverlayCache', () => {
    beforeEach(() => localStorage.removeItem('token'))

    it('多次调用幂等且不抛错', () => {
      expect(() => {
        resetOverlayCache()
        resetOverlayCache()
      }).not.toThrow()
    })

    it('reset 后 mergeBackendMessages 可再次尝试拉取（无 token 时短路，不发请求）', async () => {
      const axiosGet = vi.spyOn(axios, 'get')
      localStorage.removeItem('token')


      resetOverlayCache()
      await mergeBackendMessages('zh-CN')

      // 无 token 短路：不应触发 axios 请求
      expect(axiosGet).not.toHaveBeenCalled()
      axiosGet.mockRestore()
    })
  })
})
