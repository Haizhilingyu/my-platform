import { describe, it, expect, vi, beforeEach } from 'vitest'

const { captors } = vi.hoisted(() => ({
  captors: {
    requestHandler: null as ((config: any) => any) | null,
    responseSuccessHandler: null as ((response: any) => any) | null,
    responseErrorHandler: null as ((error: any) => any) | null,
  },
}))

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => ({
      interceptors: {
        request: { use: vi.fn((h: any) => { captors.requestHandler = h }) },
        response: {
          use: vi.fn((success: any, error: any) => {
            captors.responseSuccessHandler = success
            captors.responseErrorHandler = error
          }),
        },
      },
    })),
  },
}))

import '@/modules/sys/api/http'

describe('http', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
    delete (window as any).location
    ;(window as any).location = { href: '' }
  })

  describe('request interceptor', () => {
    it('should add Authorization header when token exists in localStorage', () => {
      localStorage.setItem('token', 'abc123')
      const config = { headers: {} as Record<string, string> }

      const result = captors.requestHandler!(config)

      expect(result.headers.Authorization).toBe('Bearer abc123')
    })

    it('should not add Authorization header when no token in localStorage', () => {
      const config = { headers: {} as Record<string, string> }

      const result = captors.requestHandler!(config)

      expect(result.headers.Authorization).toBeUndefined()
    })
  })

  describe('response success handler', () => {
    it('should return response.data (unwrap axios envelope)', () => {
      const response = { data: { code: 0, message: 'ok', data: [] } }

      const result = captors.responseSuccessHandler!(response)

      expect(result).toEqual({ code: 0, message: 'ok', data: [] })
    })
  })

  describe('response error handler', () => {
    it('should clear token and redirect to /login on 401 when token was present', async () => {
      localStorage.setItem('token', 'old-token')
      const error = {
        response: { status: 401 },
        config: { headers: { Authorization: 'Bearer old-token' } },
      }

      await expect(captors.responseErrorHandler!(error)).rejects.toEqual(error)
      expect(localStorage.getItem('token')).toBeNull()
      expect(window.location.href).toBe('/login')
    })

    it('should not redirect on 401 when no token was present', async () => {
      const error = {
        response: { status: 401 },
        config: { headers: {} },
      }

      await expect(captors.responseErrorHandler!(error)).rejects.toEqual(error)
      expect(window.location.href).toBe('')
    })

    it('should just reject without redirect or token removal on non-401 errors', async () => {
      localStorage.setItem('token', 'some-token')
      const error = {
        response: { status: 500 },
        config: {},
      }

      await expect(captors.responseErrorHandler!(error)).rejects.toEqual(error)
      expect(localStorage.getItem('token')).toBe('some-token')
      expect(window.location.href).toBe('')
    })
  })
})
