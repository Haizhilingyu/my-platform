import { describe, it, expect, beforeEach, vi } from 'vitest'
import { useAiChat } from '@/modules/ai/composables/useAiChat'
import { watch } from 'vue'
import { streamChat, fetchHistory, deleteHistoryMessage } from '@/modules/ai/api/ai'

/**
 * useAiChat 对话状态逻辑测试。
 *
 * 覆盖：
 *  - send：用户消息入列、SSE token 累积、done 后 pending 复位；
 *  - 空文本 / 流式中重复发送被忽略；
 *  - 流式失败时错误落到 assistant 消息；
 *  - 二次确认执行 / 取消状态流转；
 *  - stop 中断流；
 *  - loadHistory 从服务端加载历史（含 id）；
 *  - deleteMessage 调 API 并移除本地消息。
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

/** 让 streamChat 按脚本驱动 handlers，模拟一次正常 SSE 会话。 */
function mockStreamScript(script: (h: Handlers) => void) {
  vi.mocked(streamChat).mockImplementation(async (_msg, handlers: Handlers) => {
    script(handlers)
  })
}

describe('useAiChat', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(fetchHistory).mockResolvedValue([])
    vi.mocked(deleteHistoryMessage).mockResolvedValue(undefined)
  })

  it('send 推送用户消息并通过 token 累积助手回复', async () => {
    mockStreamScript((h) => {
      h.onToken('你')
      h.onToken('好')
      h.onDone()
    })
    const { messages, streaming, send } = useAiChat()

    await send(' 你好 ')

    expect(streaming.value).toBe(false)
    expect(messages.value).toHaveLength(2)
    expect(messages.value[0]).toMatchObject({ role: 'user', text: '你好' })
    expect(messages.value[1]).toMatchObject({ role: 'assistant', text: '你好', pending: false })
  })

  it('流式 token 增量触发响应式更新（UI 实时渲染的前提）', async () => {
    mockStreamScript((h) => {
      h.onToken('a')
      h.onToken('b')
      h.onDone()
    })
    const { messages, send } = useAiChat()
    const snapshots: string[] = []
    // 回归 bug：handlersFor 闭包里的 assistant 指向 push 前的原始对象，
    // 修改它不会触发响应式更新。用 watch 追踪 text 变化验证代理正确。
    const stop = watch(
      () => messages.value[messages.value.length - 1]?.text,
      (v) => snapshots.push(v ?? ''),
      { flush: 'sync' },
    )
    await send('hi')
    stop()
    expect(snapshots.length).toBeGreaterThanOrEqual(2)
  })

  it('空文本不发送', async () => {
    mockStreamScript((h) => h.onDone())
    const { messages, send } = useAiChat()
    await send('   ')
    expect(messages.value).toHaveLength(0)
    expect(streamChat).not.toHaveBeenCalled()
  })

  it('流式中重复发送被忽略', async () => {
    mockStreamScript((h) => h.onDone())
    const { send } = useAiChat()
    const p1 = send('first')
    await send('second')
    await p1
    expect(streamChat).toHaveBeenCalledTimes(1)
  })

  it('流式失败时错误文本落到助手消息并标记 error', async () => {
    vi.mocked(streamChat).mockRejectedValue(new Error('网络错误'))
    const { messages, send } = useAiChat()
    await send('hi')
    const assistant = messages.value[messages.value.length - 1]
    expect(assistant.error).toBe(true)
    expect(assistant.text).toBe('网络错误')
  })

  it('onError 事件直接写入错误文本', async () => {
    mockStreamScript((h) => h.onError('服务器错误'))
    const { messages, send } = useAiChat()
    await send('hi')
    expect(messages.value[messages.value.length - 1].text).toBe('服务器错误')
  })

  it('工具与动作事件挂到助手消息并回调 onAction', async () => {
    mockStreamScript((h) => {
      h.onTool({ name: 'listUsers' })
      h.onResult('查询完成')
      h.onAction({ path: '/sys/user', highlightId: 3 })
      h.onDone()
    })
    const { messages, send } = useAiChat()
    let actionPath = ''
    await send('查询', (a) => (actionPath = a.path))
    const assistant = messages.value[messages.value.length - 1]
    expect(assistant.tool).toBe('listUsers')
    expect(assistant.text).toBe('查询完成')
    expect(assistant.action).toEqual({ path: '/sys/user', highlightId: 3 })
    expect(actionPath).toBe('/sys/user')
  })

  it('confirmExecute 回传确认载荷并推进确认状态', async () => {
    mockStreamScript((h) => h.onDone())
    const { messages, send, confirmExecute } = useAiChat()
    await send('删除用户')
    const origin = messages.value[messages.value.length - 1]
    await confirmExecute(origin, { tool: 'deleteUser', args: { id: 5 }, message: '确认删除？' })
    expect(origin.confirmState).toBe('executed')
  })

  it('confirmCancel 仅更新确认状态，不发起请求', async () => {
    mockStreamScript((h) => h.onDone())
    const { messages, send, confirmCancel } = useAiChat()
    await send('删除用户')
    const origin = messages.value[messages.value.length - 1]
    confirmCancel(origin)
    expect(origin.confirmState).toBe('cancelled')
  })

  it('stop 中断进行中的流并复位 streaming', async () => {
    mockStreamScript(() => {
      // 不调用 onDone，模拟流挂起
    })
    const { streaming, send, stop } = useAiChat()
    const p = send('hi')
    stop()
    await p
    expect(streaming.value).toBe(false)
  })

  it('loadHistory 用服务端历史填充消息（含 id）', async () => {
    vi.mocked(fetchHistory).mockResolvedValue([
      { id: 1, role: 'user', text: '历史问题' },
      { id: 2, role: 'assistant', text: '历史答复' },
    ])
    const { messages, loadHistory } = useAiChat()
    await loadHistory()
    expect(messages.value).toHaveLength(2)
    expect(messages.value[0]).toMatchObject({ id: 1, role: 'user', text: '历史问题' })
    expect(messages.value[1]).toMatchObject({ id: 2, role: 'assistant', text: '历史答复' })
  })

  it('loadHistory 失败保持空消息', async () => {
    vi.mocked(fetchHistory).mockRejectedValue(new Error('网络错误'))
    const { messages, loadHistory } = useAiChat()
    await loadHistory()
    expect(messages.value).toHaveLength(0)
  })

  it('deleteMessage 调 API 并移除本地消息', async () => {
    vi.mocked(fetchHistory).mockResolvedValue([
      { id: 1, role: 'user', text: '问题' },
      { id: 2, role: 'assistant', text: '答复' },
    ])
    const { messages, loadHistory, deleteMessage } = useAiChat()
    await loadHistory()
    const target = messages.value[0]
    await deleteMessage(target)
    expect(deleteHistoryMessage).toHaveBeenCalledWith(1)
    expect(messages.value).toHaveLength(1)
    expect(messages.value[0].text).toBe('答复')
  })

  it('deleteMessage 失败时保留消息并抛出', async () => {
    vi.mocked(fetchHistory).mockResolvedValue([{ id: 1, role: 'user', text: '问题' }])
    vi.mocked(deleteHistoryMessage).mockRejectedValue(new Error('404'))
    const { messages, loadHistory, deleteMessage } = useAiChat()
    await loadHistory()
    const target = messages.value[0]
    await expect(deleteMessage(target)).rejects.toThrow('404')
    expect(messages.value).toHaveLength(1)
  })

  it('无 id 消息 deleteMessage 不发起请求', async () => {
    mockStreamScript((h) => h.onDone())
    const { messages, send, deleteMessage } = useAiChat()
    await send('hi')
    const userMsg = messages.value[0]
    await deleteMessage(userMsg)
    expect(deleteHistoryMessage).not.toHaveBeenCalled()
  })
})
