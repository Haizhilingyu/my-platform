import { describe, it, expect } from 'vitest'
import { parseSseBlock, decodePayload } from '../ai'

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
