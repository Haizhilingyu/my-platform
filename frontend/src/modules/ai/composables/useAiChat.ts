import { ref } from 'vue'
import { streamChat, type AiActionEvent } from '@/modules/ai/api/ai'

export interface ChatMessage {
  role: 'user' | 'assistant'
  text: string
  tool?: string
  action?: AiActionEvent
  pending?: boolean
  error?: boolean
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
    const assistant: ChatMessage = { role: 'assistant', text: '', pending: true }
    messages.value.push(assistant)
    streaming.value = true
    controller = new AbortController()
    try {
      await streamChat(
        content,
        {
          onTool: (e) => {
            assistant.tool = e.name
          },
          onResult: (t) => {
            assistant.text = t
          },
          onToken: (t) => {
            assistant.text += t
          },
          onAction: (a) => {
            assistant.action = a
            onAction?.(a)
          },
          onError: (t) => {
            assistant.text = t
            assistant.error = true
          },
          onDone: () => {
            assistant.pending = false
          },
        },
        controller.signal,
      )
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

  function stop(): void {
    controller?.abort()
    streaming.value = false
  }

  function clear(): void {
    messages.value = []
  }

  return { messages, streaming, send, stop, clear }
}
