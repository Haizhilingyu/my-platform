import { describe, it, expect, beforeEach, vi } from 'vitest'
import router from '@/router/index'

/**
 * 路由守卫行为测试。
 *
 * 覆盖：
 *  - public 路由（login / 404）未登录可直达；
 *  - 未登录访问受保护路由重定向 Login；
 *  - 已登录但权限未加载时先 fetchUserInfo，失败则 logout 并重定向 Login；
 *  - 已登录但无页面级权限时重定向 Dashboard；
 *  - 有权限时正常放行。
 */

const { authState } = vi.hoisted(() => ({
  authState: {
    isLoggedIn: false,
    permissions: new Set<string>(),
    fetchUserInfo: vi.fn(),
    hasPermission: vi.fn(),
    logout: vi.fn(),
  },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authState,
}))

describe('router 全局前置守卫', () => {
  beforeEach(async () => {
    vi.clearAllMocks()
    authState.isLoggedIn = false
    authState.permissions = new Set()
    authState.fetchUserInfo.mockResolvedValue(undefined)
    authState.hasPermission.mockReturnValue(false)
    // 统一回到 public 登录页：vue-router 对同路由重复跳转会短路，guard 不再执行。
    await router.push('/login')
  })

  it('未登录可直达 public 登录页', async () => {
    await router.push('/login')
    expect(router.currentRoute.value.name).toBe('Login')
  })

  it('未登录访问受保护路由重定向到 Login', async () => {
    await router.push('/sys/user')
    expect(router.currentRoute.value.name).toBe('Login')
  })

  it('未匹配路径命中 public 404 页，不要求登录', async () => {
    await router.push('/no-such-page')
    expect(router.currentRoute.value.name).toBe('NotFound')
  })

  it('已登录但权限未加载时先拉取用户信息再放行', async () => {
    authState.isLoggedIn = true
    authState.fetchUserInfo.mockImplementation(async () => {
      authState.permissions = new Set(['sys:user:list'])
    })
    authState.hasPermission.mockImplementation((p: string) => authState.permissions.has(p))

    await router.push('/sys/user')

    expect(authState.fetchUserInfo).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.name).toBe('SysUser')
  })

  it('拉取用户信息失败时登出并重定向 Login', async () => {
    authState.isLoggedIn = true
    authState.fetchUserInfo.mockRejectedValue(new Error('401'))

    await router.push('/sys/user')

    expect(authState.logout).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.name).toBe('Login')
  })

  it('已登录但缺少页面级权限时重定向 Dashboard', async () => {
    authState.isLoggedIn = true
    authState.permissions = new Set(['sys:role:list'])
    authState.hasPermission.mockImplementation((p: string) => authState.permissions.has(p))

    await router.push('/sys/menu')

    expect(router.currentRoute.value.name).toBe('Dashboard')
  })

  it('已登录且具备权限时放行到目标页', async () => {
    authState.isLoggedIn = true
    authState.permissions = new Set(['sys:role:list'])
    authState.hasPermission.mockImplementation((p: string) => authState.permissions.has(p))

    await router.push('/sys/role')

    expect(router.currentRoute.value.name).toBe('SysRole')
  })

  it('遍历全部受保护路由触发各视图懒加载', async () => {
    authState.isLoggedIn = true
    authState.permissions = new Set([
      'sys:user:list',
      'sys:role:list',
      'sys:menu:list',
      'sys:unit:list',
      'sys:config:list',
      'sys:openapp:list',
      'sys:audit:list',
      'sys:i18n:list',
    ])
    authState.hasPermission.mockImplementation((p: string) => authState.permissions.has(p))

    const paths = [
      '/dashboard',
      '/sys/user',
      '/sys/role',
      '/sys/menu',
      '/sys/unit',
      '/sys/config',
      '/sys/app',
      '/sys/audit',
      '/sys/session',
      '/sys/i18n',
    ]
    for (const p of paths) {
      await router.push(p)
      expect(router.currentRoute.value.path).toBe(p)
    }
  })

  it('无 permission meta 的受保护路由（session）登录即可访问', async () => {
    authState.isLoggedIn = true
    authState.permissions = new Set(['sys:role:list'])
    authState.hasPermission.mockImplementation((p: string) => authState.permissions.has(p))

    await router.push('/sys/session')

    expect(router.currentRoute.value.name).toBe('SysSession')
  })
})
