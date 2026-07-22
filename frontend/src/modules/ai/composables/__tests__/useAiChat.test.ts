import { describe, it, expect, beforeEach, vi } from 'vitest'
import { useAiChat } from '@/modules/ai/composables/useAiChat'
import { watch } from 'vue'
import { streamChat } from '@/modules/ai/api/ai'

/**
 * useAiChat 对话状态逻辑测试。
 *
 * 覆盖：
 *  - send：用户消息入列、SSE token 累积、done 后 pending 复位；
 *  - 空文本 / 流式中重复发送被忽略；
 *  - 流式失败时错误落到 assistant 消息；
 *  - 二次确认执行 / 取消状态流转；
 *  - stop 中断流、clear 清空会话。
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
    localStorage.removeItem('ai-chat-history')
    // useAiChat 的 messages 是模块级单例（ref(loadHistory())），跨用例需重置
    useAiChat().clear()
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
    // 回归 bug：handlersFor 闭包里的 assistant 指向 push 前的原始对象，
    // 修改它不触发 Vue 响应式 → token 到达但 UI 不更新，直到下一次数组变动才"补刷"。
    const seenTexts: string[] = []

    const { messages, send } = useAiChat()

    watch(
      () => messages.value[messages.value.length - 1]?.text ?? '',
      (txt) => seenTexts.push(txt),
      { flush: 'sync' },
    )

    mockStreamScript((h) => {
      h.onToken('你')
      h.onToken('好')
      h.onDone()
    })
    await send('hi')

    // 每个 token 到达都应触发响应式回调，捕获序列至少包含 '你' 和 '你好'
    expect(seenTexts).toContain('你')
    expect(seenTexts).toContain('你好')
  })

  it('空文本不发送', async () => {
    const { messages, send } = useAiChat()
    await send('   ')
    expect(messages.value).toHaveLength(0)
    expect(streamChat).not.toHaveBeenCalled()
  })

  it('流式中重复发送被忽略', async () => {
    let resolveStream!: () => void
    vi.mocked(streamChat).mockImplementation(() => new Promise<void>((r) => (resolveStream = r)))
    const { messages, streaming, send } = useAiChat()

    const p1 = send('第一条')
    expect(streaming.value).toBe(true)
    await send('第二条')
    expect(streamChat).toHaveBeenCalledTimes(1)

    resolveStream()
    await p1
    expect(messages.value).toHaveLength(2) // 1 用户 + 1 助手，无第二条
  })

  it('流式失败时错误文本落到助手消息并标记 error', async () => {
    vi.mocked(streamChat).mockRejectedValue(new Error('网络中断'))
    const { messages, send } = useAiChat()

    await send('hi')

    expect(messages.value[1]).toMatchObject({ text: '网络中断', error: true, pending: false })
  })

  it('onError 事件直接写入错误文本', async () => {
    mockStreamScript((h) => {
      h.onError('服务不可用')
      h.onDone()
    })
    const { messages, send } = useAiChat()

    await send('hi')
    expect(messages.value[1]).toMatchObject({ text: '服务不可用', error: true })
  })

  it('工具与动作事件挂到助手消息并回调 onAction', async () => {
    const action = { type: 'navigate', path: '/sys/user' }
    mockStreamScript((h) => {
      h.onTool({ name: 'query_user' })
      h.onAction(action)
      h.onResult('查到了')
      h.onDone()
    })
    const onAction = vi.fn()
    const { messages, send } = useAiChat()

    await send('查用户', onAction)

    expect(messages.value[1]).toMatchObject({ tool: 'query_user', text: '查到了', action })
    expect(onAction).toHaveBeenCalledWith(action)
  })

  it('confirmExecute 回传确认载荷并推进确认状态', async () => {
    mockStreamScript((h) => {
      h.onConfirm({ tool: 'delete_user', args: { id: 5 }, message: '确认删除用户 5？' })
      h.onDone()
    })
    const { messages, send, confirmExecute } = useAiChat()
    await send('删除用户 5')

    const origin = messages.value[1]
    expect(origin.confirm).toBeTruthy()
    expect(origin.confirmState).toBe('pending')

    vi.mocked(streamChat).mockClear()
    mockStreamScript((h) => h.onDone())
    await confirmExecute(origin, origin.confirm as never)

    expect(origin.confirmState).toBe('executed')
    expect(streamChat).toHaveBeenCalledWith(
      '确认删除用户 5？',
      expect.anything(),
      expect.anything(),
      { tool: 'delete_user', args: { id: 5 } },
      expect.anything(),
    )
    expect(messages.value[2]).toMatchObject({ role: 'user', text: '✓ 确认删除用户 5？' })
  })

  it('confirmCancel 仅更新确认状态，不发起请求', async () => {
    mockStreamScript((h) => {
      h.onConfirm({ tool: 'delete_user', args: {}, message: '确认？' })
      h.onDone()
    })
    const { messages, send, confirmCancel } = useAiChat()
    await send('删除')

    vi.mocked(streamChat).mockClear()
    confirmCancel(messages.value[1])

    expect(messages.value[1].confirmState).toBe('cancelled')
    expect(streamChat).not.toHaveBeenCalled()
  })

  it('stop 中断进行中的流并复位 streaming', async () => {
    let signal!: AbortSignal
    vi.mocked(streamChat).mockImplementation(
      (_m, _h, sig: AbortSignal) =>
        new Promise<void>((_resolve, reject) => {
          signal = sig
          sig.addEventListener('abort', () => reject(new Error('已取消')))
        }),
    )
    const { streaming, send, stop } = useAiChat()

    const p = send('hi')
    expect(streaming.value).toBe(true)
    stop()
    expect(signal.aborted).toBe(true)
    expect(streaming.value).toBe(false)
    await p
  })

  it('clear 清空全部消息', async () => {
    mockStreamScript((h) => h.onDone())
    const { messages, send, clear } = useAiChat()
    await send('hi')
    expect(messages.value.length).toBeGreaterThan(0)

    clear()
    expect(messages.value).toHaveLength(0)
  })
})
