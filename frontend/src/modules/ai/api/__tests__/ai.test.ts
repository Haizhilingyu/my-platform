import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { parseSseBlock, decodePayload, streamChat, type AiHandlers } from '../ai'

describe('parseSseBlock', () => {
  it('parses event and data lines', () => {
    const b = parseSseBlock('event:tool\ndata:{"name":"createUser","args":{"username":"bob"}}')
    expect(b.event).toBe('tool')
    expect(b.data).toBe('{"name":"createUser","args":{"username":"bob"}}')
  })

  it('defaults event to message', () => {
    expect(parseSseBlock('data:hello').event).toBe('message')
  })

  it('joins multi-line data', () => {
    const b = parseSseBlock('event:token\ndata:line1\ndata:line2')
    expect(b.data).toBe('line1\nline2')
  })
})

describe('decodePayload', () => {
  it('decodes tool/action as object', () => {
    const p = decodePayload('action', '{"path":"/sys/user","highlightId":3}')
    expect(p).toEqual({ path: '/sys/user', highlightId: 3 })
  })

  it('decodes result as raw string (Spring sends bare string)', () => {
    expect(decodePayload('result', '已创建用户 bob（id=3）')).toBe('已创建用户 bob（id=3）')
  })

  it('decodes JSON-quoted string token', () => {
    expect(decodePayload('token', '"hello"')).toBe('hello')
  })

  it('tolerates malformed tool json', () => {
    expect(decodePayload('tool', 'not-json')).toEqual({})
  })
})

/** 把字符串按 chunkSize 切块，构造 fetch Response.body 风格的 reader。 */
function readerFrom(text: string, chunkSize = text.length) {
  const encoder = new TextEncoder()
  const chunks: Uint8Array[] = []
  for (let i = 0; i < text.length; i += chunkSize) {
    chunks.push(encoder.encode(text.slice(i, i + chunkSize)))
  }
  let idx = 0
  return {
    read: vi.fn(async () => {
      if (idx >= chunks.length) return { value: undefined, done: true }
      return { value: chunks[idx++], done: false }
    }),
  }
}

function mockSseResponse(body: string, opts: { ok?: boolean; status?: number; chunkSize?: number } = {}) {
  const { ok = true, status = 200, chunkSize } = opts
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => ({
      ok,
      status,
      body: ok ? { getReader: () => readerFrom(body, chunkSize) } : null,
    })),
  )
}

describe('streamChat', () => {
  beforeEach(() => {
    localStorage.setItem('token', 'test-token')
    localStorage.setItem('locale', 'zh-CN')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    localStorage.clear()
  })

  it('POST 到 /ai/chat 并携带鉴权与语言头', async () => {
    mockSseResponse('event:done\ndata:{}\n\n')
    await streamChat('你好', {})

    expect(fetch).toHaveBeenCalledWith(
      '/api/ai/chat',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          Authorization: 'Bearer test-token',
          'Accept-Language': 'zh-CN',
        }),
        body: JSON.stringify({ message: '你好' }),
      }),
    )
  })

  it('confirm 参数合并进请求体', async () => {
    mockSseResponse('event:done\ndata:{}\n\n')
    await streamChat('确认删除', {}, undefined, { tool: 'delete_user', args: { id: 5 } })

    const body = JSON.parse(vi.mocked(fetch).mock.calls[0][1]!.body as string)
    expect(body).toEqual({ message: '确认删除', confirm: { tool: 'delete_user', args: { id: 5 } } })
  })

  it('按序分发 token/result/done 事件', async () => {
    const sse =
      'event:token\ndata:你\n\n' +
      'event:token\ndata:好\n\n' +
      'event:result\ndata:查询完成\n\n' +
      'event:done\ndata:{}\n\n'
    mockSseResponse(sse)
    const calls: string[] = []
    const handlers: AiHandlers = {
      onToken: (t) => calls.push(`token:${t}`),
      onResult: (t) => calls.push(`result:${t}`),
      onDone: () => calls.push('done'),
    }

    await streamChat('hi', handlers)

    expect(calls).toEqual(['token:你', 'token:好', 'result:查询完成', 'done'])
  })

  it('SSE 块被拆到多个 chunk 时仍能正确重组', async () => {
    const sse = 'event:token\ndata:完整的一句话\n\nevent:done\ndata:{}\n\n'
    mockSseResponse(sse, { chunkSize: 5 }) // 每 5 字节一个 chunk，必然切断事件块
    const tokens: string[] = []
    let done = false

    await streamChat('hi', { onToken: (t) => tokens.push(t), onDone: () => (done = true) })

    expect(tokens).toEqual(['完整的一句话'])
    expect(done).toBe(true)
  })

  it('流末尾无空行结尾的残余 buffer 也会被分发', async () => {
    mockSseResponse('event:token\ndata:尾巴') // 没有 \n\n 结束符
    const tokens: string[] = []

    await streamChat('hi', { onToken: (t) => tokens.push(t) })

    expect(tokens).toEqual(['尾巴'])
  })

  it('tool/action/confirm/error 事件分发到对应 handler', async () => {
    const sse =
      'event:tool\ndata:{"name":"create_user","args":{"u":"bob"}}\n\n' +
      'event:action\ndata:{"path":"/sys/user","highlightId":3}\n\n' +
      'event:confirm\ndata:{"tool":"delete_user","args":{},"message":"确认？"}\n\n' +
      'event:error\ndata:出错了\n\n'
    mockSseResponse(sse)
    const seen: Record<string, unknown> = {}

    await streamChat('hi', {
      onTool: (e) => (seen.tool = e),
      onAction: (e) => (seen.action = e),
      onConfirm: (e) => (seen.confirm = e),
      onError: (t) => (seen.error = t),
    })

    expect(seen.tool).toEqual({ name: 'create_user', args: { u: 'bob' } })
    expect(seen.action).toEqual({ path: '/sys/user', highlightId: 3 })
    expect(seen.confirm).toEqual({ tool: 'delete_user', args: {}, message: '确认？' })
    expect(seen.error).toBe('出错了')
  })

  it('HTTP 非 2xx 时抛错', async () => {
    mockSseResponse('', { ok: false, status: 401 })
    await expect(streamChat('hi', {})).rejects.toThrow('HTTP 401')
  })
})
