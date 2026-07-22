import { ref } from 'vue'
import {
  streamChat,
  fetchHistory,
  deleteHistoryMessage,
  type AiActionEvent,
  type AiConfirmEvent,
} from '@/modules/ai/api/ai'

export interface ChatMessage {
  role: 'user' | 'assistant'
  text: string
  /** 数据库主键；服务端加载的消息有值，本轮新发的消息在下次打开面板前为 undefined。 */
  id?: number
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

/** AI 对话状态与发送逻辑。 */
export function useAiChat() {
  const messages = ref<ChatMessage[]>([])
  const streaming = ref(false)
  let controller: AbortController | null = null

  /** 从服务端加载最近历史（失败时保持空面板，不打扰用户）。 */
  async function loadHistory(): Promise<void> {
    try {
      const list = await fetchHistory()
      messages.value = list.map((m) => ({ id: m.id, role: m.role, text: m.text, ts: Date.now() }))
    } catch {
      // 忽略：面板保持欢迎态
    }
  }

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

  /** 单条删除：成功后从本地移除；失败抛错由调用方提示。无 id（未持久化）直接忽略。 */
  async function deleteMessage(m: ChatMessage): Promise<void> {
    if (m.id == null) return
    await deleteHistoryMessage(m.id)
    messages.value = messages.value.filter((x) => x !== m)
  }

  async function run(
    message: string,
    confirm: { tool: string; args: Record<string, unknown> } | undefined,
    onAction?: (a: AiActionEvent) => void,
  ): Promise<void> {
    messages.value.push({ role: 'assistant', text: '', pending: true, ts: Date.now() })
    // push 后取回响应式代理引用 —— 后续 token/tool/action 增量修改必须经过代理才触发 Vue 响应式
    const assistant = messages.value[messages.value.length - 1]
    streaming.value = true
    controller = new AbortController()
    try {
      await streamChat(message, handlersFor(assistant, onAction), controller.signal, confirm)
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

  function handlersFor(assistant: ChatMessage, onAction?: (a: AiActionEvent) => void) {
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

  return {
    messages,
    streaming,
    send,
    confirmExecute,
    confirmCancel,
    stop,
    loadHistory,
    deleteMessage,
  }
}
