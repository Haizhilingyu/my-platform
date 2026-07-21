import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import MessageCenter from '@/shared/components/MessageCenter.vue'

/**
 * MessageCenter 消息推送组件测试。
 *
 * 覆盖：
 *  - WS 消息分发：URGENT → 紧急弹窗；IMPORTANT → notification；NORMAL → 仅未读计数；
 *  - 登录后连接 WS 并刷新未读数；
 *  - 未读数接口失败时静默降级；
 *  - 紧急弹窗"查看详情"跳转收件箱。
 */

const {
  authState,
  notifyState,
  wsMock,
  notificationMock,
  routerMock,
} = vi.hoisted(() => ({
  authState: { isLoggedIn: true, token: 'tok-1' },
  notifyState: {
    currentUrgent: null as unknown,
    incrementUnread: vi.fn(),
    setUnread: vi.fn(),
    pushUrgent: vi.fn(),
    dismissUrgent: vi.fn(),
  },
  wsMock: {
    connect: vi.fn(),
    disconnect: vi.fn(),
    onMessage: vi.fn(),
  },
  notificationMock: { info: vi.fn(), success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  routerMock: { push: vi.fn() },
}))

vi.mock('@/stores/auth', () => ({ useAuthStore: () => authState }))
vi.mock('@/stores/notify', () => ({ useNotifyStore: () => notifyState }))
vi.mock('@/shared/composables/useWebSocket', () => ({ useWebSocket: () => wsMock }))
vi.mock('@/shared/composables/useBreakpoint', () => ({
  useBreakpoint: () => ({ isMobile: { value: false } }),
}))
vi.mock('vue-router', () => ({ useRouter: () => routerMock }))
vi.mock('@/shared/api/notify', () => ({
  notifyApi: { unreadCount: vi.fn().mockResolvedValue({ data: 3 }) },
}))

vi.mock('naive-ui', async () => {
  const actual = await vi.importActual<typeof import('naive-ui')>('naive-ui')
  return { ...actual, useNotification: () => notificationMock }
})

import { notifyApi } from '@/shared/api/notify'

function mountCenter() {
  return mount(MessageCenter, { global: { plugins: [createPinia()] } })
}

/** 取出组件注册到 useWebSocket 的消息处理器。 */
function capturedHandler(): (raw: unknown) => void {
  expect(wsMock.onMessage).toHaveBeenCalled()
  return wsMock.onMessage.mock.calls[0][0]
}

describe('MessageCenter.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authState.isLoggedIn = true
    authState.token = 'tok-1'
    notifyState.currentUrgent = null
    vi.mocked(notifyApi.unreadCount).mockResolvedValue({ data: 3 } as never)
  })

  it('已登录挂载时连接 WS 并刷新未读数', async () => {
    mountCenter()
    await flushPromises()

    expect(wsMock.connect).toHaveBeenCalledWith(expect.stringContaining('/ws/notify'), 'tok-1')
    expect(notifyState.setUnread).toHaveBeenCalledWith(3)
  })

  it('未登录时不连接 WS', () => {
    authState.isLoggedIn = false
    mountCenter()
    expect(wsMock.connect).not.toHaveBeenCalled()
  })

  it('URGENT 消息进入紧急弹窗并递增未读', () => {
    mountCenter()
    capturedHandler()({ type: 'notify', level: 'URGENT', messageId: 9, title: '系统告警', content: '存储将满' })

    expect(notifyState.incrementUnread).toHaveBeenCalled()
    expect(notifyState.pushUrgent).toHaveBeenCalledWith({
      messageId: 9,
      title: '系统告警',
      content: '存储将满',
    })
    expect(notificationMock.info).not.toHaveBeenCalled()
  })

  it('IMPORTANT 消息走 notification 提示', () => {
    mountCenter()
    capturedHandler()({ level: 'IMPORTANT', title: '版本发布', content: 'v1.1 已上线' })

    expect(notifyState.incrementUnread).toHaveBeenCalled()
    expect(notificationMock.info).toHaveBeenCalledWith(
      expect.objectContaining({ title: '版本发布', content: 'v1.1 已上线' }),
    )
    expect(notifyState.pushUrgent).not.toHaveBeenCalled()
  })

  it('NORMAL 消息仅递增未读计数', () => {
    mountCenter()
    capturedHandler()({ level: 'NORMAL', title: '常规通知' })

    expect(notifyState.incrementUnread).toHaveBeenCalled()
    expect(notifyState.pushUrgent).not.toHaveBeenCalled()
    expect(notificationMock.info).not.toHaveBeenCalled()
  })

  it('非法消息体被忽略', () => {
    mountCenter()
    capturedHandler()(null)
    capturedHandler()('not-an-object')

    expect(notifyState.incrementUnread).not.toHaveBeenCalled()
  })

  it('未读数接口失败时静默降级不报错', async () => {
    vi.mocked(notifyApi.unreadCount).mockRejectedValue(new Error('404'))
    mountCenter()
    await flushPromises()

    expect(notifyState.setUnread).not.toHaveBeenCalled()
  })

  it('卸载时断开 WS', () => {
    const wrapper = mountCenter()
    wrapper.unmount()
    expect(wsMock.disconnect).toHaveBeenCalled()
  })
  it('goToInbox 关闭紧急弹窗并跳转收件箱', async () => {
    const wrapper = mountCenter()
    await flushPromises()
    const ss = (wrapper.vm as unknown as { $: { setupState: Record<string, unknown> } }).$.setupState

    ;(ss.goToInbox as () => void)()
    await flushPromises()

    expect(notifyState.dismissUrgent).toHaveBeenCalled()
    expect(routerMock.push).toHaveBeenCalledWith('/sys/message')
  })
})
