import { ref, type Ref } from 'vue'

export type WebSocketConnectionState =
  | 'idle'
  | 'connecting'
  | 'open'
  | 'closing'
  | 'closed'

export type WebSocketMessageHandler = (data: unknown) => void

export interface UseWebSocketReturn {
  state: Ref<WebSocketConnectionState>
  connect: (url: string, token: string) => void
  disconnect: () => void
  onMessage: (handler: WebSocketMessageHandler) => void
}

/**
 * WebSocket composable 骨架：仅暴露稳定的类型与函数签名，
 * 供 T19 / T20 等下游任务提前对齐调用约定。
 *
 * TODO: T20 implements actual WebSocket logic
 */
export function useWebSocket(): UseWebSocketReturn {
  const state = ref<WebSocketConnectionState>('idle')

  function connect(_url: string, _token: string): void {
    // TODO: T20 implements actual WebSocket logic
  }

  function disconnect(): void {
    // TODO: T20 implements actual WebSocket logic
  }

  function onMessage(_handler: WebSocketMessageHandler): void {
    // TODO: T20 implements actual WebSocket logic
  }

  return { state, connect, disconnect, onMessage }
}
