import { test, expect } from '@playwright/test'
import { loggedInPage } from '../fixtures/helpers'
import { apiLogin, extractJti } from '../fixtures/auth'
import { redisExists, getRedis } from '../fixtures/redis'

const BASE = process.env.E2E_BASE_URL || 'http://localhost:8090'

test.describe('在线会话 + Bug1 回归', () => {
  test('登录后会话列表 API 状态码（Bug 1 监控）', async () => {
    const token = await apiLogin()
    const res = await fetch(`${BASE}/api/sys/auth/sessions`, {
      headers: { Authorization: `Bearer ${token}` },
    })

    expect(res.status).toBe(200)

    if (res.status === 200) {
      const json: any = await res.json()
      expect(json.success).toBe(true)
      expect(Array.isArray(json.data)).toBe(true)
    }
  })

  test('UI 会话页可访问', async ({ page }) => {
    await loggedInPage(page, '/sys/session')
    await expect(page.getByText('我的会话').first()).toBeVisible({ timeout: 10_000 })
  })

  test('撤销自己的会话 → Redis 中 session:active:{jti} 被删除', async () => {
    const token = await apiLogin()
    const jti = extractJti(token)
    expect(await redisExists(`session:active:${jti}`)).toBe(true)

    // 后端 API 撤销：UI 撤销按钮在 NPopconfirm 内，且撤销后该 token 失效，浏览器后续请求都会失败
    const res = await fetch(`${BASE}/api/sys/auth/sessions/${jti}/revoke`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(res.status).toBe(200)

    await new Promise((r) => setTimeout(r, 300))
    expect(await redisExists(`session:active:${jti}`)).toBe(false)
  })

  test('登录后 Redis 写入 session:active:{jti}（Bug 1 数据完整性）', async () => {
    const token = await apiLogin()
    const jti = extractJti(token)
    // 即便 listSessions 因反序列化失败返回 500，写入侧正常 —— 校验原始 key 存在
    const r = getRedis()
    const raw = await r.get(`session:active:${jti}`)
    expect(raw).toBeTruthy()
    const parsed = JSON.parse(raw)
    expect(parsed.jti).toBe(jti)
  })
})
