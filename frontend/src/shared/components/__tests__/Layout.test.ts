import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { NMenu, NDropdown, NPopover, NLayoutSider, NButton } from 'naive-ui'
import { MoonOutline } from '@vicons/ionicons5'
import Layout from '@/shared/components/Layout.vue'
import ChatPanel from '@/modules/ai/views/ChatPanel.vue'
import { notifyApi } from '@/shared/api/notify'

/**
 * Layout 应用外壳组件测试。
 *
 * 覆盖：
 *  - 侧边菜单按 visible 过滤渲染，点击触发路由跳转；
 *  - 用户下拉 logout → 登出并回登录页；
 *  - 铃铛下拉打开时拉取最近未读，"查看全部"跳消息中心；
 *  - 语言切换 / 主题切换；
 *  - AI 气泡 action 跳转（带 highlight query，对话框保持开启）；
 *  - 移动端不渲染 sider。
 */

const { authState, themeState, notifyState, localeState, bpState, routeState, routerMock } =
  vi.hoisted(() => ({
    authState: {
      user: { username: 'admin', realName: '管理员' },
      menus: [
        {
          id: 1,
          menuName: '系统管理',
          path: '',
          visible: 1,
          icon: 'Settings',
          children: [
            {
              id: 2,
              menuName: '用户管理',
              path: '/sys/user',
              visible: 1,
              icon: 'User',
              children: [],
            },
            { id: 3, menuName: '隐藏菜单', path: '/sys/hidden', visible: 0, children: [] },
          ],
        },
      ],
      logout: vi.fn(),
    },
    themeState: { isDark: false, toggle: vi.fn() },
    notifyState: { unreadCount: 5 },
    localeState: { setLocale: vi.fn() },
    bpState: { isMobile: false, breakpoint: 'desktop' as string },
    routeState: { path: '/dashboard' },
    routerMock: { push: vi.fn() },
  }))

vi.mock('@/stores/auth', () => ({ useAuthStore: () => authState }))
vi.mock('@/stores/theme', () => ({ useThemeStore: () => themeState }))
vi.mock('@/stores/notify', () => ({ useNotifyStore: () => notifyState }))
vi.mock('@/stores/locale', () => ({ useLocaleStore: () => localeState }))
vi.mock('vue-router', () => ({
  useRouter: () => routerMock,
  useRoute: () => ({ path: routeState.path, query: {}, meta: {} }),
}))
vi.mock('@/shared/composables/useBreakpoint', async () => {
  const { ref } = await import('vue')
  return {
    useBreakpoint: () => ({
      isMobile: ref(bpState.isMobile),
      breakpoint: ref(bpState.breakpoint),
    }),
  }
})
vi.mock('@/shared/api/notify', () => ({
  notifyApi: {
    inbox: vi.fn().mockResolvedValue({
      data: {
        list: [
          {
            id: 1,
            title: '未读一',
            level: 'URGENT',
            readStatus: false,
            createdAt: '2026-07-01T10:00:00Z',
          },
        ],
        total: 1,
      },
    }),
  },
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
    useDialog: () => ({
      warning: vi.fn(),
      create: vi.fn(),
      info: vi.fn(),
      success: vi.fn(),
      error: vi.fn(),
    }),
  }
})

function mountLayout() {
  return mount(Layout, {
    global: {
      plugins: [createPinia()],
      stubs: { RouterView: { template: '<div />' } },
    },
  })
}

describe('Layout.vue 应用外壳', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    bpState.isMobile = false
    bpState.breakpoint = 'desktop'
    routeState.path = '/dashboard'
  })

  it('渲染菜单并按 visible=0 过滤隐藏项（options 层断言，NMenu 子项默认折叠不渲染）', () => {
    const wrapper = mountLayout()
    const options = wrapper.findComponent(NMenu).props('options') ?? []
    const labels = JSON.stringify(options)

    expect(labels).toContain('用户管理')
    expect(labels).not.toContain('隐藏菜单')
  })

  it('点击菜单项触发路由跳转', () => {
    const wrapper = mountLayout()
    wrapper.findComponent(NMenu).vm.$emit('update:value', '/sys/user')
    expect(routerMock.push).toHaveBeenCalledWith('/sys/user')
  })

  it('用户下拉选择 logout 时登出并回登录页', () => {
    const wrapper = mountLayout()
    // NMenu 内部也渲染 NDropdown，按 options 内容定位用户菜单下拉
    const userDropdown = wrapper
      .findAllComponents(NDropdown)
      .find((d) => JSON.stringify(d.props('options')).includes('logout'))
    expect(userDropdown).toBeTruthy()
    userDropdown!.vm.$emit('select', 'logout')

    expect(authState.logout).toHaveBeenCalled()
    expect(routerMock.push).toHaveBeenCalledWith('/login')
  })

  it('语言下拉切换调用 localeStore.setLocale', () => {
    const wrapper = mountLayout()
    const localeDropdown = wrapper
      .findAllComponents(NDropdown)
      .find((d) => JSON.stringify(d.props('options')).includes('zh-CN'))
    expect(localeDropdown).toBeTruthy()
    localeDropdown!.vm.$emit('select', 'en')

    expect(localeState.setLocale).toHaveBeenCalledWith('en')
  })

  it('主题按钮点击调用 themeStore.toggle', async () => {
    const wrapper = mountLayout()
    // isDark=false 时主题按钮内渲染 MoonOutline 图标，按图标定位按钮
    const themeBtn = wrapper
      .findAllComponents(NButton)
      .find((b) => b.findComponent(MoonOutline).exists())
    expect(themeBtn).toBeTruthy()
    await themeBtn!.trigger('click')

    expect(themeState.toggle).toHaveBeenCalled()
  })

  it('打开铃铛下拉拉取最近 5 条未读消息', async () => {
    const wrapper = mountLayout()
    // 页面上有多个 NPopover（menu/dropdown 内部），按 width=320 定位铃铛下拉
    const bell = wrapper.findAllComponents(NPopover).find((p) => p.props('width') === 320)
    expect(bell).toBeTruthy()
    bell!.vm.$emit('update:show', true)
    await flushPromises()

    expect(notifyApi.inbox).toHaveBeenCalledWith({ pageNum: 1, pageSize: 5, readStatus: false })
  })

  /** 点击右下角 AI 气泡按钮，等待 ChatPanel 懒渲染挂载。 */
  async function openAiBubble(wrapper: VueWrapper): Promise<void> {
    // FAB 在 Teleport(to=body) 内，按组件类型 + aria-label 定位（VTU 跨 teleport 追踪组件树）
    const aiBtn = wrapper
      .findAllComponents(NButton)
      .find((b) => b.attributes('aria-label') === 'AI 助手')
    expect(aiBtn, '右下角 AI 气泡按钮应存在').toBeTruthy()
    await aiBtn!.trigger('click')
    await flushPromises()
  }

  it('点击右下角气泡按钮打开聊天面板', async () => {
    const wrapper = mountLayout()
    expect(wrapper.findComponent(ChatPanel).exists()).toBe(false)

    await openAiBubble(wrapper)

    expect(wrapper.findComponent(ChatPanel).exists()).toBe(true)
  })

  it('AI 助手 action 跳转且保持气泡对话框开启（带 highlight）', async () => {
    const wrapper = mountLayout()
    await openAiBubble(wrapper)

    wrapper.findComponent(ChatPanel).vm.$emit('action', { path: '/sys/user', highlightId: 5 })
    await flushPromises()

    expect(routerMock.push).toHaveBeenCalledWith({
      path: '/sys/user',
      query: { highlight: '5' },
    })
    // 跳转不应关闭对话框：用户需看到 AI 回复，页面在面板下方跳转
    expect(wrapper.findComponent(ChatPanel).exists()).toBe(true)
  })

  it('AI action 无 highlightId 时只带 path 跳转，对话框保持开启', async () => {
    const wrapper = mountLayout()
    await openAiBubble(wrapper)
    wrapper.findComponent(ChatPanel).vm.$emit('action', { path: '/sys/role' })
    await flushPromises()

    expect(routerMock.push).toHaveBeenCalledWith({ path: '/sys/role' })
    expect(wrapper.findComponent(ChatPanel).exists()).toBe(true)
  })

  it('移动端不渲染 sider，改用抽屉', () => {
    bpState.isMobile = true
    bpState.breakpoint = 'mobile'
    const wrapper = mountLayout()

    expect(wrapper.findComponent(NLayoutSider).exists()).toBe(false)
  })
  it('goToInbox 关闭铃铛下拉并跳转收件箱', async () => {
    const wrapper = mountLayout()
    await flushPromises()
    const ss = (wrapper.vm as unknown as { $: { setupState: Record<string, unknown> } }).$
      .setupState

    ;(ss.goToInbox as () => void)()
    await flushPromises()

    expect(routerMock.push).toHaveBeenCalledWith('/sys/message')
  })
})
