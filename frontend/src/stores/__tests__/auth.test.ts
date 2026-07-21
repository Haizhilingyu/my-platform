import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/modules/sys/api/auth'
import { mergeBackendMessages, resetOverlayCache } from '@/i18n'

vi.mock('@/modules/sys/api/auth', () => ({
  authApi: {
    login: vi.fn(),
    getPermissions: vi.fn(),
    getMenus: vi.fn(),
    getMe: vi.fn(),
  },
}))

// 真实的 mergeBackendMessages 会走 http 拉取后端文案，单测里用 mock 屏蔽网络副作用；
// resetOverlayCache 同样 stub。保留 i18n 默认导出（store 内读取 i18n.global.locale.value）。
vi.mock('@/i18n', async () => {
  const actual = await vi.importActual<typeof import('@/i18n')>('@/i18n')
  return {
    ...actual,
    mergeBackendMessages: vi.fn().mockResolvedValue(undefined),
    resetOverlayCache: vi.fn(),
  }
})

/**
 * Auth Store 测试。
 *
 * TDD 示范：测试命名 should...when... 结构。
 */
describe('Auth Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  describe('初始状态', () => {
    it('should have empty token when initialized', () => {
      const store = useAuthStore()
      expect(store.token).toBe('')
      expect(store.isLoggedIn).toBe(false)
    })

    it('should have empty permissions when initialized', () => {
      const store = useAuthStore()
      expect(store.permissions.size).toBe(0)
    })
  })

  describe('hasPermission', () => {
    it('should return true when user has the permission', () => {
      const store = useAuthStore()
      store.permissions = new Set(['sys:user:add', 'sys:role:list'])

      expect(store.hasPermission('sys:user:add')).toBe(true)
    })

    it('should return false when user lacks the permission', () => {
      const store = useAuthStore()
      store.permissions = new Set(['sys:user:add'])

      expect(store.hasPermission('sys:user:delete')).toBe(false)
    })

    it('should return false when permissions set is empty', () => {
      const store = useAuthStore()

      expect(store.hasPermission('sys:user:add')).toBe(false)
    })
  })

  describe('logout', () => {
    it('should clear all state when logout', () => {
      const store = useAuthStore()
      store.token = 'some-token'
      store.permissions = new Set(['sys:user:add'])
      localStorage.setItem('token', 'some-token')

      store.logout()

      expect(store.token).toBe('')
      expect(store.permissions.size).toBe(0)
      expect(store.isLoggedIn).toBe(false)
      expect(localStorage.getItem('token')).toBeNull()
    })
  })

  describe('setToken', () => {
    it('should store token in state and localStorage', () => {
      const store = useAuthStore()
      store.setToken('jwt-abc-123')

      expect(store.token).toBe('jwt-abc-123')
      expect(localStorage.getItem('token')).toBe('jwt-abc-123')
    })
  })
})

const mockedAuthApi = {
  login: authApi.login as unknown as Mock,
  getPermissions: authApi.getPermissions as unknown as Mock,
  getMenus: authApi.getMenus as unknown as Mock,
  getMe: authApi.getMe as unknown as Mock,
}
const mockedMerge = mergeBackendMessages as unknown as Mock

describe('Auth Store 异步动作', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('login 应保存 token 并拉取用户信息与合并后端文案', async () => {
    mockedAuthApi.login.mockResolvedValue({ code: 0, message: 'ok', data: { token: 'tok', user: { id: 1 } } })
    mockedAuthApi.getPermissions.mockResolvedValue({ data: ['sys:user:list'] })
    mockedAuthApi.getMenus.mockResolvedValue({ data: [{ name: 'Dashboard' }] })
    mockedAuthApi.getMe.mockResolvedValue({ data: { id: 1, username: 'admin' } })

    const store = useAuthStore()
    await store.login({ username: 'admin', password: 'x', captchaId: 'c', captchaCode: '1' })

    expect(store.token).toBe('tok')
    expect(localStorage.getItem('token')).toBe('tok')
    expect(store.permissions.has('sys:user:list')).toBe(true)
    expect(store.menus).toHaveLength(1)
    expect(mockedMerge).toHaveBeenCalled()
  })

  it('fetchUserInfo 应并发拉取权限 / 菜单 / 用户', async () => {
    mockedAuthApi.getPermissions.mockResolvedValue({ data: ['*'] })
    mockedAuthApi.getMenus.mockResolvedValue({ data: [{ name: 'm' }] })
    mockedAuthApi.getMe.mockResolvedValue({ data: { id: 2 } })

    const store = useAuthStore()
    await store.fetchUserInfo()

    expect(mockedAuthApi.getPermissions).toHaveBeenCalledTimes(1)
    expect(mockedAuthApi.getMenus).toHaveBeenCalledTimes(1)
    expect(mockedAuthApi.getMe).toHaveBeenCalledTimes(1)
    expect(store.user?.id).toBe(2)
    expect(store.hasPermission('anything')).toBe(true) // 通配符 * 放行任意权限
  })

  it('fetchMenus 应仅拉取菜单并写入 state', async () => {
    mockedAuthApi.getMenus.mockResolvedValue({ data: [{ name: 'menu-a' }, { name: 'menu-b' }] })

    const store = useAuthStore()
    await store.fetchMenus()

    expect(mockedAuthApi.getMenus).toHaveBeenCalledTimes(1)
    expect(store.menus).toHaveLength(2)
  })

  it('logout 应清空 state 并重置文案缓存', () => {
    const store = useAuthStore()
    store.token = 'tok'
    store.permissions = new Set(['sys:user:list'])
    localStorage.setItem('token', 'tok')

    store.logout()

    expect(store.token).toBe('')
    expect(store.permissions.size).toBe(0)
    expect(store.user).toBeNull()
    expect(store.menus).toHaveLength(0)
    expect(resetOverlayCache).toHaveBeenCalled()
  })
})
