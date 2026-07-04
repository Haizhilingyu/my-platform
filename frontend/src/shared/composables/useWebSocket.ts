import { ref, computed, type Ref, type ComputedRef } from 'vue'

export type WebSocketConnectionState =
  | 'idle'
  | 'connecting'
  | 'open'
  | 'closing'
  | 'closed'

export type WebSocketMessageHandler = (data: unknown) => void

export interface UseWebSocketReturn {
  state: Ref<WebSocketConnectionState>
  isConnected: ComputedRef<boolean>
  connect: (url: string, token: string) => void
  disconnect: () => void
  onMessage: (handler: WebSocketMessageHandler) => void
  offMessage: (handler: WebSocketMessageHandler) => void
}

const LAST_SEQ_KEY = 'notify:lastSeq'
const SEQ_FIELD = 'lastSeqReceived'

interface PushMessageEnvelope {
  type?: string
  level?: string
  messageId?: number
  seq?: number
  title?: string
  content?: string
}

// 指数退避重连：1s→2s→4s→8s…（上限 30s，最多 10 次）
const MAX_RECONNECT_ATTEMPTS = 10
const BASE_DELAY_MS = 1000
const MAX_DELAY_MS = 30_000

function getLastSeq(): number {
  const raw = typeof localStorage !== 'undefined' ? localStorage.getItem(LAST_SEQ_KEY) : null
  if (!raw) return 0
  const n = Number(raw)
  return Number.isFinite(n) && n > 0 ? n : 0
}

function setLastSeq(seq: number): void {
  if (!Number.isFinite(seq) || seq <= 0) return
  if (seq > getLastSeq() && typeof localStorage !== 'undefined') {
    localStorage.setItem(LAST_SEQ_KEY, String(seq))
  }
}

function readSeq(payload: unknown): number | undefined {
  if (payload && typeof payload === 'object' && 'seq' in (payload as Record<string, unknown>)) {
    const v = (payload as PushMessageEnvelope).seq
    return typeof v === 'number' && Number.isFinite(v) ? v : undefined
  }
  return undefined
}

function buildWsUrl(url: string, token: string): string {
  const sep = url.includes('?') ? '&' : '?'
  return `${url}${sep}token=${encodeURIComponent(token)}`
}

/**
 * Message-center WebSocket composable (instance-scoped).
 *
 * On open sends the replay handshake `{lastSeqReceived: N}` (N persisted in
 * localStorage). Reconnect uses exponential backoff 1s→2s→4s… capped at 30s,
 * max 10 attempts; an explicit `disconnect()` suppresses reconnect. Intended
 * to be created once per app (the caller holds the single connection).
 */
export function useWebSocket(): UseWebSocketReturn {
  const state = ref<WebSocketConnectionState>('idle')
  const isConnected = computed(() => state.value === 'open')

  let socket: WebSocket | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let reconnectAttempts = 0
  let currentUrl = ''
  let currentToken = ''
  let manuallyClosed = false
  const handlers = new Set<WebSocketMessageHandler>()

  function clearReconnectTimer(): void {
    if (reconnectTimer !== null) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  function scheduleReconnect(): void {
    if (manuallyClosed) return
    if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) return
    if (!currentUrl || !currentToken) return
    clearReconnectTimer()
    const delay = Math.min(BASE_DELAY_MS * 2 ** reconnectAttempts, MAX_DELAY_MS)
    reconnectAttempts += 1
    reconnectTimer = setTimeout(() => {
      doConnect(currentUrl, currentToken)
    }, delay)
  }

  function doConnect(url: string, token: string): void {
    if (typeof WebSocket === 'undefined') return
    clearReconnectTimer()
    if (socket) {
      try {
        socket.close()
      } catch {
        /* ignore */
      }
    }
    state.value = 'connecting'
    let ws: WebSocket
    try {
      ws = new WebSocket(buildWsUrl(url, token))
    } catch {
      scheduleReconnect()
      return
    }
    socket = ws

    ws.onopen = () => {
      state.value = 'open'
      reconnectAttempts = 0
      try {
        ws.send(JSON.stringify({ [SEQ_FIELD]: getLastSeq() }))
      } catch {
        /* ignore */
      }
    }

    ws.onmessage = (event: MessageEvent) => {
      let parsed: unknown = event.data
      if (typeof event.data === 'string') {
        try {
          parsed = JSON.parse(event.data)
        } catch {
          /* non-JSON payload: hand raw string downstream */
        }
      }
      const seq = readSeq(parsed)
      if (seq !== undefined) setLastSeq(seq)
      handlers.forEach((h) => {
        try {
          h(parsed)
        } catch {
          /* isolate handler errors from connection stability */
        }
      })
    }

    // onerror intentionally empty: reconnect is driven solely by onclose to avoid double scheduling.
    ws.onerror = () => {}

    ws.onclose = () => {
      state.value = 'closed'
      socket = null
      scheduleReconnect()
    }
  }

  function connect(url: string, token: string): void {
    if (!url || !token) return
    currentUrl = url
    currentToken = token
    manuallyClosed = false
    reconnectAttempts = 0
    doConnect(url, token)
  }

  function disconnect(): void {
    manuallyClosed = true
    clearReconnectTimer()
    reconnectAttempts = 0
    if (socket) {
      state.value = 'closing'
      try {
        socket.close()
      } catch {
        /* ignore */
      }
      socket = null
    }
    state.value = 'closed'
  }

  function onMessage(handler: WebSocketMessageHandler): void {
    handlers.add(handler)
  }

  function offMessage(handler: WebSocketMessageHandler): void {
    handlers.delete(handler)
  }

  return { state, isConnected, connect, disconnect, onMessage, offMessage }
}
