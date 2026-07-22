/**
 * AI Copilot SSE 客户端。后端 POST /api/ai/chat 返回 text/event-stream：
 * event: tool|result|token|action|done|error
 *   - tool/action 的 data 是 JSON 对象
 *   - result/token/error 的 data 是裸字符串（Spring 对 String 未做 JSON 包裹）
 */

import { http } from '@/modules/sys/api/http'
import type { Result } from '@/modules/sys/api/types'

export interface AiToolEvent {
  name: string
  args: Record<string, unknown>
}

export interface AiActionEvent {
  path: string
  highlightId?: number | null
}

export interface AiConfirmEvent {
  tool: string
  args: Record<string, unknown>
  message: string
}

export interface AiHandlers {
  onTool?: (e: AiToolEvent) => void
  onResult?: (text: string) => void
  onToken?: (text: string) => void
  onAction?: (e: AiActionEvent) => void
  onConfirm?: (e: AiConfirmEvent) => void
  onError?: (text: string) => void
  onDone?: () => void
}

export interface SseBlock {
  event: string
  data: string
}

/** 解析单个 SSE 事件块（event:/data: 行）。纯函数，便于单测。 */
export function parseSseBlock(block: string): SseBlock {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5))
    }
  }
  return { event, data: dataLines.join('\n') }
}

/**
 * 按 event 类型解码 data 负载：
 * tool/action → 解析为对象；result/token/error → 取字符串（兼容裸串与 JSON 引号串）。
 */
export function decodePayload(event: string, raw: string): unknown {
  if (event === 'tool' || event === 'action' || event === 'confirm') {
    try {
      return JSON.parse(raw)
    } catch {
      return {}
    }
  }
  try {
    const v = JSON.parse(raw)
    return typeof v === 'string' ? v : raw
  } catch {
    return raw
  }
}

function dispatch(block: string, h: AiHandlers): void {
  const { event, data } = parseSseBlock(block)
  const payload = decodePayload(event, data)
  switch (event) {
    case 'tool':
      h.onTool?.(payload as AiToolEvent)
      break
    case 'action':
      h.onAction?.(payload as AiActionEvent)
      break
    case 'confirm':
      h.onConfirm?.(payload as AiConfirmEvent)
      break
    case 'result':
      h.onResult?.(payload as string)
      break
    case 'token':
      h.onToken?.(payload as string)
      break
    case 'error':
      h.onError?.(payload as string)
      break
    case 'done':
      h.onDone?.()
      break
    default:
      break
  }
}

/** 发起对话并以流式回调接收事件。 */
export async function streamChat(
  message: string,
  handlers: AiHandlers,
  signal?: AbortSignal,
  confirm?: { tool: string; args: Record<string, unknown> },
): Promise<void> {
  const base = import.meta.env.VITE_API_BASE_URL || '/api'
  const token = localStorage.getItem('token')
  const locale = localStorage.getItem('locale') || 'zh-CN'
  const body: Record<string, unknown> = { message }
  if (confirm) body.confirm = confirm
  const resp = await fetch(`${base}/ai/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: token ? `Bearer ${token}` : '',
      'Accept-Language': locale,
    },
    body: JSON.stringify(body),
    signal,
  })
  if (!resp.ok || !resp.body) {
    throw new Error(`HTTP ${resp.status}`)
  }
  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  for (;;) {
    const { value, done } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    let idx: number
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      const block = buffer.slice(0, idx)
      buffer = buffer.slice(idx + 2)
      if (block.trim()) {
        dispatch(block, handlers)
      }
    }
  }
  if (buffer.trim()) {
    dispatch(buffer, handlers)
  }
}

export interface AiHistoryMessage {
  id: number
  role: 'user' | 'assistant'
  text: string
}

/** 拉取当前用户最近 10 条历史（时间升序，含消息 id 供单条删除）。 */
export async function fetchHistory(): Promise<AiHistoryMessage[]> {
  const res = (await http.get('/ai/chat/history')) as Result<AiHistoryMessage[]>
  return res.data ?? []
}

/** 单条删除历史消息；不存在/非本人消息时后端返回 404。 */
export async function deleteHistoryMessage(id: number): Promise<void> {
  await http.delete(`/ai/chat/history/${id}`)
}
