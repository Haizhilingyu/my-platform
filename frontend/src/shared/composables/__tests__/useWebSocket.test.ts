import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { useWebSocket } from '@/shared/composables/useWebSocket'

interface MockWebSocketInstance {
  url: string
  sent: string[]
  closed: boolean
  readyState: number
  onopen: ((ev: Event) => void) | null
  onmessage: ((ev: MessageEvent) => void) | null
  onerror: ((ev: Event) => void) | null
  onclose: ((ev: CloseEvent) => void) | null
  send(data: string): void
  close(): void
}

interface MockWebSocketCtor {
  new (url: string): MockWebSocketInstance
  instances: MockWebSocketInstance[]
}

function installMockWebSocket(): MockWebSocketCtor {
  const instances: MockWebSocketInstance[] = []
  class MockWebSocket implements MockWebSocketInstance {
    url: string
    sent: string[] = []
    closed = false
    readyState = 0
    onopen = null
    onmessage = null
    onerror = null
    onclose = null
    constructor(url: string) {
      this.url = url
      instances.push(this)
    }
    send(data: string): void {
      this.sent.push(data)
    }
    close(): void {
      this.closed = true
      this.readyState = 3
    }
  }
  const ctor = MockWebSocket as unknown as MockWebSocketCtor
  ctor.instances = instances
  return ctor
}

function fireOpen(inst: MockWebSocketInstance): void {
  inst.readyState = 1
  inst.onopen?.(new Event('open'))
}

function fireMessage(inst: MockWebSocketInstance, payload: unknown): void {
  inst.onmessage?.({ data: JSON.stringify(payload) } as MessageEvent)
}

function fireError(inst: MockWebSocketInstance): void {
  inst.onerror?.(new Event('error'))
}

function fireClose(inst: MockWebSocketInstance): void {
  inst.readyState = 3
  inst.onclose?.(new CloseEvent('close'))
}

describe('useWebSocket', () => {
  let MockWebSocket: MockWebSocketCtor

  beforeEach(() => {
    vi.useFakeTimers()
    localStorage.clear()
    MockWebSocket = installMockWebSocket()
    vi.stubGlobal('WebSocket', MockWebSocket)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('should be idle initially', () => {
    const { state, isConnected } = useWebSocket()
    expect(state.value).toBe('idle')
    expect(isConnected.value).toBe(false)
  })

  it('should connect and send lastSeqReceived handshake on open', () => {
    localStorage.setItem('notify:lastSeq', '7')
    const { connect, isConnected } = useWebSocket()

    connect('ws://x/ws/notify', 'jwt-token')
    expect(MockWebSocket.instances).toHaveLength(1)
    expect(MockWebSocket.instances[0].url).toBe('ws://x/ws/notify?token=jwt-token')

    fireOpen(MockWebSocket.instances[0])
    expect(isConnected.value).toBe(true)
    expect(MockWebSocket.instances[0].sent[0]).toBe(JSON.stringify({ lastSeqReceived: 7 }))
  })

  it('should dispatch parsed messages to registered handlers', () => {
    const { connect, onMessage } = useWebSocket()
    const received: unknown[] = []
    onMessage((m) => received.push(m))

    connect('ws://x/ws/notify', 't')
    const inst = MockWebSocket.instances[0]
    fireOpen(inst)
    fireMessage(inst, { type: 'message', level: 'URGENT', seq: 12, title: 'T', content: 'C' })

    expect(received).toHaveLength(1)
    expect((received[0] as { seq: number }).seq).toBe(12)
  })

  it('should persist the highest seq to localStorage', () => {
    const { connect, onMessage } = useWebSocket()
    onMessage(() => undefined)

    connect('ws://x/ws/notify', 't')
    const inst = MockWebSocket.instances[0]
    fireOpen(inst)
    fireMessage(inst, { seq: 5 })
    fireMessage(inst, { seq: 9 })
    fireMessage(inst, { seq: 3 })

    expect(localStorage.getItem('notify:lastSeq')).toBe('9')
  })

  it('should reconnect with exponential backoff after unexpected close', async () => {
    const { connect } = useWebSocket()

    connect('ws://x/ws/notify', 't')
    const first = MockWebSocket.instances[0]
    fireOpen(first)
    fireClose(first)

    expect(MockWebSocket.instances).toHaveLength(1)
    await vi.advanceTimersByTimeAsync(1000)
    expect(MockWebSocket.instances).toHaveLength(2)

    const second = MockWebSocket.instances[1]
    fireOpen(second)
    expect(second.sent[0]).toBe(JSON.stringify({ lastSeqReceived: 0 }))
  })

  it('should not reconnect after manual disconnect', async () => {
    const { connect, disconnect } = useWebSocket()

    connect('ws://x/ws/notify', 't')
    const inst = MockWebSocket.instances[0]
    fireOpen(inst)
    disconnect()

    expect(inst.closed).toBe(true)
    await vi.advanceTimersByTimeAsync(60_000)
    expect(MockWebSocket.instances).toHaveLength(1)
  })

  it('should stop reconnecting after max attempts', async () => {
    const { connect } = useWebSocket()

    connect('ws://x/ws/notify', 't')
    fireClose(MockWebSocket.instances[0])

    // 每次重连创建的新 socket 都立即失败关闭，不触发 onopen（避免重置 attempts）
    for (let i = 0; i < 15; i += 1) {
      await vi.advanceTimersByTimeAsync(30_000)
      const latest = MockWebSocket.instances[MockWebSocket.instances.length - 1]
      if (latest && !latest.closed) {
        fireClose(latest)
      }
    }
    // 首次连接 + 最多 10 次重连 = 至多 11 个实例
    expect(MockWebSocket.instances.length).toBeLessThanOrEqual(11)
  })

  it('should isolate a throwing handler from other handlers', () => {
    const { connect, onMessage } = useWebSocket()
    const second: unknown[] = []
    onMessage(() => {
      throw new Error('boom')
    })
    onMessage((m) => second.push(m))

    connect('ws://x/ws/notify', 't')
    const inst = MockWebSocket.instances[0]
    fireOpen(inst)
    fireMessage(inst, { seq: 1 })

    expect(second).toHaveLength(1)
  })

  it('should remove handler via offMessage', () => {
    const { connect, onMessage, offMessage } = useWebSocket()
    const received: unknown[] = []
    const handler = (m: unknown): void => {
      received.push(m)
    }
    onMessage(handler)

    connect('ws://x/ws/notify', 't')
    const inst = MockWebSocket.instances[0]
    fireOpen(inst)
    fireMessage(inst, { seq: 1 })
    offMessage(handler)
    fireMessage(inst, { seq: 2 })

    expect(received).toHaveLength(1)
  })

  it('should guard when WebSocket runtime is unavailable', async () => {
    vi.unstubAllGlobals()
    vi.stubGlobal('WebSocket', undefined)
    const { connect, state } = useWebSocket()

    connect('ws://x/ws/notify', 't')
    await vi.advanceTimersByTimeAsync(60_000)
    expect(state.value).not.toBe('open')
  })

  it('连接后 onerror 被赋值且触发不抛错（重连由 onclose 驱动）', () => {
    const { connect } = useWebSocket()
    connect('ws://x/test', 'tok')
    const inst = MockWebSocket.instances[0]
    expect(inst.onerror).not.toBeNull()
    // onerror 故意留空：触发它不应改变连接状态、不应安排重连
    expect(() => fireError(inst)).not.toThrow()
  })
})
