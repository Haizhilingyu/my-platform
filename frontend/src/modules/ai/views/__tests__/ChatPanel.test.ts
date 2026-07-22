import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { NMessageProvider, NPopconfirm } from 'naive-ui'
import ChatPanel from '@/modules/ai/views/ChatPanel.vue'
import { streamChat, fetchHistory, deleteHistoryMessage } from '@/modules/ai/api/ai'

/**
 * AI 对话面板组件测试。
 *
 * 覆盖：
 *  - 空态展示欢迎语与示例按钮；
 *  - 输入框回车 / 发送按钮提交，消息气泡渲染；
 *  - 流式中输入框禁用；
 *  - 动作按钮向父组件抛出 action 事件；
 *  - 二次确认卡片执行 / 取消；
 *  - 挂载后加载服务端历史；
 *  - 带 id 消息渲染删除入口；
 *  - 确认删除后气泡消失。
 */

type Handlers = {
  onTool: (e: { name: string }) => void
  onResult: (t: string) => void
  onToken: (t: string) => void
  onAction: (a: unknown) => void
  onConfirm: (c: unknown) => void
  onError: (t: string) => void
  onDone: () => void
}

vi.mock('@/modules/ai/api/ai', () => ({
  streamChat: vi.fn(),
  fetchHistory: vi.fn(),
  deleteHistoryMessage: vi.fn(),
}))

function mockStreamScript(script: (h: Handlers) => void) {
  vi.mocked(streamChat).mockImplementation(async (_msg, handlers: Handlers) => {
    script(handlers)
  })
}

// ChatPanel 调 useMessage()，需 NMessageProvider 祖先提供注入上下文
const PanelHost = defineComponent({
  setup() {
    return () => h(NMessageProvider, null, { default: () => h(ChatPanel) })
  },
})

function mountPanel() {
  return mount(PanelHost, { attachTo: document.body })
}

async function submitText(wrapper: ReturnType<typeof mountPanel>, text: string) {
  const textarea = wrapper.find('textarea')
  expect(textarea.exists()).toBe(true)
  await textarea.setValue(text)
  await textarea.trigger('keydown.enter')
  await flushPromises()
}

describe('ChatPanel.vue AI 对话面板', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(fetchHistory).mockResolvedValue([])
    vi.mocked(deleteHistoryMessage).mockResolvedValue(undefined)
  })

  it('空态展示示例按钮，点击示例直接发起对话', async () => {
    mockStreamScript((h) => {
      h.onResult('好的')
      h.onDone()
    })
    const wrapper = mountPanel()
    await flushPromises()
    const exampleBtns = wrapper.findAll('button').filter((b) => b.text().length > 4)
    expect(exampleBtns.length).toBeGreaterThanOrEqual(2)
    for (const btn of exampleBtns.slice(0, 2)) {
      await btn.trigger('click')
      await flushPromises()
    }

    expect(streamChat).toHaveBeenCalledTimes(exampleBtns.length >= 2 ? 2 : 1)
    expect(wrapper.text()).toContain('好的')
  })

  it('回车提交消息并渲染用户与助手气泡', async () => {
    mockStreamScript((h) => {
      h.onToken('已查询')
      h.onDone()
    })
    const wrapper = mountPanel()

    await submitText(wrapper, '查一下用户列表')

    // streamChat 现在 4 参（message, handlers, signal, confirm）——无 history
    expect(streamChat).toHaveBeenCalledWith(
      '查一下用户列表',
      expect.anything(),
      expect.anything(),
      undefined,
    )
    expect(wrapper.text()).toContain('查一下用户列表')
    expect(wrapper.text()).toContain('已查询')
  })

  it('提交后输入框被清空', async () => {
    mockStreamScript((h) => h.onDone())
    const wrapper = mountPanel()

    await submitText(wrapper, '你好')

    expect((wrapper.find('textarea').element as HTMLTextAreaElement).value).toBe('')
  })

  it('流式进行中输入框禁用', async () => {
    let resolveStream!: () => void
    vi.mocked(streamChat).mockImplementation(() => new Promise<void>((r) => (resolveStream = r)))
    const wrapper = mountPanel()

    const textarea = wrapper.find('textarea')
    await textarea.setValue('hi')
    await textarea.trigger('keydown.enter')
    await flushPromises()

    expect(wrapper.find('textarea').attributes('disabled')).toBeDefined()

    resolveStream()
    await flushPromises()
    expect(wrapper.find('textarea').attributes('disabled')).toBeUndefined()
  })

  it('动作按钮向父组件抛出 action 事件', async () => {
    const action = { type: 'navigate', path: '/sys/user?highlight=5' }
    mockStreamScript((h) => {
      h.onResult('已创建')
      h.onAction(action)
      h.onDone()
    })
    const wrapper = mountPanel()

    await submitText(wrapper, '创建用户')
    const viewBtn = wrapper.findAll('button').find((b) => b.classes().includes('mt-2'))
    expect(viewBtn).toBeTruthy()
    await viewBtn!.trigger('click')

    // ChatPanel 嵌套在 PanelHost 内，事件需从组件实例取
    const panel = wrapper.findComponent(ChatPanel)
    expect(panel.emitted('action')?.at(-1)).toEqual([action])
  })

  it('二次确认：点执行后回传确认载荷', async () => {
    const confirm = { tool: 'delete_user', args: { id: 5 }, message: '确认删除用户 5？' }
    mockStreamScript((h) => {
      h.onConfirm(confirm)
      h.onDone()
    })
    const wrapper = mountPanel()
    await submitText(wrapper, '删除用户 5')
    expect(wrapper.text()).toContain('确认删除用户 5？')

    vi.mocked(streamChat).mockClear()
    mockStreamScript((h) => h.onDone())

    const execBtn = wrapper
      .findAll('button')
      .find((b) => b.classes().includes('n-button--error-type'))
    expect(execBtn).toBeTruthy()
    await execBtn!.trigger('click')
    await flushPromises()

    expect(streamChat).toHaveBeenCalledWith(
      '确认删除用户 5？',
      expect.anything(),
      expect.anything(),
      { tool: 'delete_user', args: { id: 5 } },
    )
  })

  it('二次确认：点取消后不再发起请求', async () => {
    mockStreamScript((h) => {
      h.onConfirm({ tool: 'delete_user', args: {}, message: '确认删除？' })
      h.onDone()
    })
    const wrapper = mountPanel()
    await submitText(wrapper, '删除')

    vi.mocked(streamChat).mockClear()
    const cancelBtn = wrapper.findAll('button').find((b) => b.text().trim() === '取消')
    expect(cancelBtn).toBeTruthy()
    await cancelBtn!.trigger('click')
    await flushPromises()

    expect(streamChat).not.toHaveBeenCalled()
  })

  it('挂载后加载服务端历史并渲染气泡（空态不渲染）', async () => {
    vi.mocked(fetchHistory).mockResolvedValue([
      { id: 10, role: 'user', text: '历史问题' },
      { id: 11, role: 'assistant', text: '历史答复' },
    ])
    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.text()).toContain('历史问题')
    expect(wrapper.text()).toContain('历史答复')
  })

  it('带 id 消息渲染删除入口（NPopconfirm），无 id 消息不渲染', async () => {
    vi.mocked(fetchHistory).mockResolvedValue([{ id: 10, role: 'user', text: '历史消息' }])
    mockStreamScript((h) => h.onDone())
    const wrapper = mountPanel()
    await flushPromises()

    // 历史消息（带 id）应有 1 个 NPopconfirm（删除入口）
    const popconfirmsBefore = wrapper.findAllComponents(NPopconfirm)
    expect(popconfirmsBefore.length).toBe(1)

    // 发一条新消息（无 id）→ 不应有新增删除入口
    await submitText(wrapper, '新消息')
    const popconfirmsAfter = wrapper.findAllComponents(NPopconfirm)
    expect(popconfirmsAfter.length).toBe(1)
  })

  it('确认删除后气泡消失并调用 API', async () => {
    vi.mocked(fetchHistory).mockResolvedValue([{ id: 10, role: 'user', text: '待删消息' }])
    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.text()).toContain('待删消息')

    // 直接触发 NPopconfirm 的 positive-click 事件（模拟用户点确认）
    const popconfirm = wrapper.findComponent(NPopconfirm)
    expect(popconfirm.exists()).toBe(true)
    popconfirm.vm.$emit('positive-click')
    await flushPromises()

    expect(deleteHistoryMessage).toHaveBeenCalledWith(10)
    expect(wrapper.text()).not.toContain('待删消息')
  })
})
