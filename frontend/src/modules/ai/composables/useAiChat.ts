import { ref, watch } from 'vue'
import { streamChat, type AiActionEvent, type AiConfirmEvent } from '@/modules/ai/api/ai'

export interface ChatMessage {
  role: 'user' | 'assistant'
  text: string
  tool?: string
  action?: AiActionEvent
  pending?: boolean
  error?: boolean
  /** 破坏性工具二次确认（pending 状态展示执行/取消按钮）。 */
  confirm?: AiConfirmEvent
  confirmState?: 'pending' | 'executed' | 'cancelled'
  /** 时间戳，用于历史排序。 */
  ts?: number
}

const STORAGE_KEY = 'ai-chat-history'
const MAX_HISTORY = 10

/** AI 对话状态与发送逻辑。 */
export function useAiChat() {
  const messages = ref<ChatMessage[]>(loadHistory())
  const streaming = ref(false)
  let controller: AbortController | null = null

  // 消息变化时自动持久化（只存已完成的消息，不含 pending/error）
  watch(
    messages,
    (msgs) => {
      persist(msgs)
    },
    { deep: true },
  )

  async function send(text: string, onAction?: (a: AiActionEvent) => void): Promise<void> {
    const content = text.trim()
    if (!content || streaming.value) {
      return
    }
    messages.value.push({ role: 'user', text: content, ts: Date.now() })
    await run(content, undefined, onAction)
  }

  /** 二次确认：用户点「执行」后，回传工具调用让服务端直接执行。 */
  async function confirmExecute(
    originMsg: ChatMessage,
    ce: AiConfirmEvent,
    onAction?: (a: AiActionEvent) => void,
  ): Promise<void> {
    if (streaming.value || !ce) {
      return
    }
    originMsg.confirmState = 'executed'
    messages.value.push({ role: 'user', text: '✓ ' + ce.message, ts: Date.now() })
    await run(ce.message, { tool: ce.tool, args: ce.args }, onAction)
  }

  /** 二次确认：用户点「取消」。 */
  function confirmCancel(originMsg: ChatMessage): void {
    originMsg.confirmState = 'cancelled'
  }

  async function run(
    message: string,
    confirm: { tool: string; args: Record<string, unknown> } | undefined,
    onAction?: (a: AiActionEvent) => void,
  ): Promise<void> {
    const assistant: ChatMessage = { role: 'assistant', text: '', pending: true, ts: Date.now() }
    messages.value.push(assistant)
    streaming.value = true
    controller = new AbortController()
    try {
      // 构建历史上下文（排除当前 pending 的 assistant），传给后端做意图理解
      const history = messages.value
        .filter((m) => !m.pending && !m.error)
        .slice(-MAX_HISTORY)
        .map((m) => ({ role: m.role, text: m.text }))
      await streamChat(message, handlersFor(assistant, onAction), controller.signal, confirm, history)
    } catch (e) {
      const msg = e instanceof Error ? e.message : '请求失败'
      if (!assistant.text) {
        assistant.text = msg
        assistant.error = true
      }
    } finally {
      assistant.pending = false
      streaming.value = false
      controller = null
    }
  }

  function handlersFor(
    assistant: ChatMessage,
    onAction?: (a: AiActionEvent) => void,
  ) {
    return {
      onToken: (t: string) => {
        assistant.text += t
      },
      onResult: (r: string) => {
        assistant.text = r
      },
      onTool: (e: { name: string }) => {
        assistant.tool = e.name
      },
      onAction: (a: AiActionEvent) => {
        assistant.action = a
        onAction?.(a)
      },
      onConfirm: (ce: AiConfirmEvent) => {
        assistant.confirm = ce
        assistant.confirmState = 'pending'
      },
      onDone: () => {
        assistant.pending = false
      },
      onError: (msg: string) => {
        assistant.text = msg
        assistant.error = true
        assistant.pending = false
      },
    }
  }

  function stop(): void {
    controller?.abort()
    controller = null
    streaming.value = false
  }

  function clear(): void {
    messages.value = []
    localStorage.removeItem(STORAGE_KEY)
  }

  return { messages, streaming, send, confirmExecute, confirmCancel, stop, clear }
}

function loadHistory(): ChatMessage[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as ChatMessage[]
    // 只恢复已完成的对话（过滤掉 pending/error 状态）
    return parsed.filter((m) => !m.pending && !m.error).slice(-MAX_HISTORY)
  } catch {
    return []
  }
}

function persist(msgs: ChatMessage[]): void {
  try {
    // 只持久化已完成的对话
    const clean = msgs.filter((m) => !m.pending && !m.error && m.text).slice(-MAX_HISTORY)
    localStorage.setItem(STORAGE_KEY, JSON.stringify(clean))
  } catch {
    // localStorage 满或隐私模式，静默失败
  }
}
