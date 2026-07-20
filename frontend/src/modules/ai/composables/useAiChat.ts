import { ref } from 'vue'
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
}

/** AI 对话状态与发送逻辑。 */
export function useAiChat() {
  const messages = ref<ChatMessage[]>([])
  const streaming = ref(false)
  let controller: AbortController | null = null

  async function send(text: string, onAction?: (a: AiActionEvent) => void): Promise<void> {
    const content = text.trim()
    if (!content || streaming.value) {
      return
    }
    messages.value.push({ role: 'user', text: content })
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
    messages.value.push({ role: 'user', text: '✓ ' + ce.message })
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
    const assistant: ChatMessage = { role: 'assistant', text: '', pending: true }
    messages.value.push(assistant)
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

  function handlersFor(
    assistant: ChatMessage,
    onAction?: (a: AiActionEvent) => void,
  ) {
    return {
      onTool: (e: { name: string }) => {
        assistant.tool = e.name
      },
      onResult: (t: string) => {
        assistant.text = t
      },
      onToken: (t: string) => {
        assistant.text += t
      },
      onAction: (a: AiActionEvent) => {
        assistant.action = a
        onAction?.(a)
      },
      onConfirm: (c: AiConfirmEvent) => {
        assistant.confirm = c
      },
      onError: (t: string) => {
        assistant.text = t
        assistant.error = true
      },
      onDone: () => {
        assistant.pending = false
      },
    }
  }

  function stop(): void {
    controller?.abort()
    streaming.value = false
  }

  function clear(): void {
    messages.value = []
  }

  return { messages, streaming, send, confirmExecute, confirmCancel, stop, clear }
}
