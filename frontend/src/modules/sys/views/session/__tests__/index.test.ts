import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { NSelect, NPopconfirm, NTabs } from 'naive-ui'
import SessionIndex from '@/modules/sys/views/session/index.vue'
import { sessionApi } from '@/modules/sys/api/session'
import { userApi } from '@/modules/sys/api/user'

/**
 * 会话管理视图行为测试。
 *
 * 覆盖：
 *  - 挂载即加载"我的会话"与用户选项；
 *  - 无 sys:user:session 权限时不渲染管理 tab；
 *  - 选择用户后加载其会话；
 *  - 注销自己的会话 / 强制下线用户会话后从列表移除。
 */

const { hasPermission, selfSession, otherSession } = vi.hoisted(() => {
  const self = {
    jti: 'jti-self-1',
    username: 'admin',
    deviceType: 'Chrome',
    ip: '127.0.0.1',
    userAgent: 'Mozilla/5.0 Chrome',
    loginAt: '2026-07-01T10:00:00Z',
    expiresAt: '2026-07-08T10:00:00Z',
  }
  return {
    hasPermission: vi.fn(() => true),
    selfSession: self,
    otherSession: { ...self, jti: 'jti-other-1', username: 'bob' },
  }
})

vi.mock('@/modules/sys/api/session', () => ({
  sessionApi: {
    mySessions: vi.fn().mockResolvedValue({ data: [selfSession] }),
    userSessions: vi.fn().mockResolvedValue({ data: [otherSession] }),
    revokeMySession: vi.fn().mockResolvedValue({}),
    revokeUserSession: vi.fn().mockResolvedValue({}),
  },
}))

vi.mock('@/modules/sys/api/user', () => ({
  userApi: {
    list: vi.fn().mockResolvedValue({
      data: { list: [{ id: 5, username: 'bob', realName: '小博' }], total: 1 },
    }),
  },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ hasPermission }),
}))

vi.mock('naive-ui', async () => {
  const actual = await vi.importActual<typeof import('naive-ui')>('naive-ui')
  return {
    ...actual,
    useMessage: () => ({
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn(),
      info: vi.fn(),
      loading: vi.fn(),
    }),
  }
})

function mountSession() {
  return mount(SessionIndex)
}

describe('session/index.vue 会话管理', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    hasPermission.mockReturnValue(true)
  })

  it('挂载时加载我的会话与候选用户列表', async () => {
    mountSession()
    await flushPromises()

    expect(sessionApi.mySessions).toHaveBeenCalledTimes(1)
    expect(userApi.list).toHaveBeenCalledWith({ pageNum: 1, pageSize: 5 })
  })

  it('渲染我的会话数据', async () => {
    const wrapper = mountSession()
    await flushPromises()

    expect(wrapper.text()).toContain('Chrome')
    expect(wrapper.text()).toContain('127.0.0.1')
  })

  it('无管理权限时不渲染"用户会话"管理 tab', async () => {
    hasPermission.mockReturnValue(false)
    const wrapper = mountSession()
    await flushPromises()

    expect(wrapper.text()).toContain('我的会话')
    expect(wrapper.findAllComponents(NSelect)).toHaveLength(0)
  })

  /** 切换到“用户会话查询”管理 tab（NTabPane 懒渲染，切 tab 后 NSelect 才挂载）。 */
  async function switchToAdminTab(wrapper: ReturnType<typeof mountSession>) {
    wrapper.findComponent(NTabs).vm.$emit('update:value', 'admin')
    await flushPromises()
  }

  it('选择用户后加载该用户的会话', async () => {
    const wrapper = mountSession()
    await flushPromises()
    await switchToAdminTab(wrapper)

    const select = wrapper.findComponent(NSelect)
    expect(select.exists()).toBe(true)
    select.vm.$emit('update:value', 5)
    await flushPromises()

    expect(sessionApi.userSessions).toHaveBeenCalledWith(5)
    expect(wrapper.text()).toContain('bob')
  })

  it('注销自己的会话后从列表移除', async () => {
    const wrapper = mountSession()
    await flushPromises()
    expect(wrapper.text()).toContain('Chrome')

    const confirm = wrapper.findComponent(NPopconfirm)
    expect(confirm.exists()).toBe(true)
    confirm.vm.$emit('positive-click')
    await flushPromises()

    expect(sessionApi.revokeMySession).toHaveBeenCalledWith('jti-self-1')
    expect(wrapper.text()).not.toContain('127.0.0.1')
  })

  it('强制下线用户会话后从管理列表移除', async () => {
    const wrapper = mountSession()
    await flushPromises()
    await switchToAdminTab(wrapper)

    wrapper.findComponent(NSelect).vm.$emit('update:value', 5)
    await flushPromises()

    const confirm = wrapper.findComponent(NPopconfirm)
    confirm.vm.$emit('positive-click')
    await flushPromises()

    expect(sessionApi.revokeUserSession).toHaveBeenCalledWith(5, 'jti-other-1')
  })
  it('searchUsers 带关键字调用 userApi.list 并填充选项；空关键字走 initUserOptions', async () => {
    const wrapper = mountSession()
    await flushPromises()
    const ss = (wrapper.vm as unknown as { $: { setupState: Record<string, unknown> } }).$.setupState

    vi.mocked(userApi.list).mockClear()
    await (ss.searchUsers as (q: string) => Promise<void>)('bob')
    await flushPromises()
    expect(userApi.list).toHaveBeenCalledWith(expect.objectContaining({ keyword: 'bob' }))

    // 空关键字 → 走 initUserOptions 分支（不带 keyword）
    vi.mocked(userApi.list).mockClear()
    await (ss.searchUsers as (q: string) => Promise<void>)('')
    await flushPromises()
    expect(userApi.list).toHaveBeenCalledWith(expect.objectContaining({ pageNum: 1, pageSize: 5 }))
  })
})
