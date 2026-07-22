import { test, expect } from '@playwright/test'
import { getCaptchaAnswer, redisExists, redisDelete, getRedis } from '../fixtures/redis'
import { apiLogin } from '../fixtures/auth'
import { execute } from '../fixtures/db'

/**
 * 账号锁定端到端测试 —— 验证 LoginSecurityService 在真实链路（API → Redis）下的行为：
 *   - 连续错误密码达到阈值后，Redis 写入 `user:lock:{username}` 标记
 *   - 锁定状态下再次登录（无论密码对错）被拒绝，无法进入系统
 *   - 锁定后通过 UI 登录仍停留在 /login 且 token 为空
 *
 * 关键约定：
 *   - 使用真实存在的测试账号 `e2e_locktest`（beforeAll 经 API 创建），因为后端
 *     LoginSecurityService 只对「用户存在但密码错误」累计失败计数——不存在的用户名
 *     直接返回「用户不存在」，永远不触发锁定。afterAll 清理该用户。
 *   - afterEach 清理 `user:lock:` / `login:fail:` 两个 Redis key，防止污染其他测试
 *   - 阈值不硬编码：循环失败直到 lock key 出现（默认 3，可由配置覆盖）
 */

const BASE = process.env.E2E_BASE_URL || 'http://localhost:8090'
const LOCK_USER = 'e2e_locktest'
const LOCK_PASSWORD = 'LockTest1!'
const FAIL_KEY = `login:fail:${LOCK_USER}`
const LOCK_KEY = `user:lock:${LOCK_USER}`

/** 一次登录尝试：自动取验证码答案，返回 login 接口 JSON 响应 */
async function attemptLogin(username: string, password: string): Promise<any> {
  const capRes = await fetch(`${BASE}/api/sys/auth/captcha`)
  if (!capRes.ok) throw new Error(`captcha request failed: HTTP ${capRes.status}`)
  const capJson: any = await capRes.json()
  const captchaId = capJson.data.captchaId
  const captchaCode = await getCaptchaAnswer(captchaId)

  const loginRes = await fetch(`${BASE}/api/sys/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, captchaId, captchaCode }),
  })
  return loginRes.json()
}

/** 反复用错误密码登录，直到 Redis 出现锁定标记（最多 10 次） */
async function bruteForceUntilLocked(username: string, password: string): Promise<boolean> {
  for (let i = 0; i < 10; i++) {
    await attemptLogin(username, password)
    if (await redisExists(LOCK_KEY)) return true
  }
  return false
}

test.beforeAll(async () => {
  // 后端只对存在的用户累计登录失败：先创建专用测试账号，否则锁定逻辑永不触发。
  const token = await apiLogin()
  await fetch(`${BASE}/api/sys/user`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: LOCK_USER, password: LOCK_PASSWORD, realName: 'lockout-test' }),
  })
})

test.afterAll(async () => {
  await execute('DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username = $1)', [LOCK_USER])
  await execute('DELETE FROM sys_user WHERE username = $1', [LOCK_USER])
})

test.describe('账号锁定（LoginSecurityService 端到端）', () => {
  test.afterEach(async () => {
    // 清理锁定 / 失败计数 key，避免污染其他测试与后续运行
    await redisDelete(LOCK_KEY)
    await redisDelete(FAIL_KEY)
  })

  test('连续错误密码达阈值后账号被锁定（Redis 标记写入）', async () => {
    const locked = await bruteForceUntilLocked(LOCK_USER, 'definitely-wrong-password')

    // 核心断言：锁定机制在真实链路下触发，Redis 标记出现
    expect(locked).toBe(true)

    // 失败计数 key 也应存在且 >= 阈值
    const r = getRedis()
    const failCount = parseInt((await r.get(FAIL_KEY)) || '0', 10)
    expect(failCount).toBeGreaterThanOrEqual(1)
  })

  test('锁定状态下再次登录被拒绝（无法获得 token）', async () => {
    const locked = await bruteForceUntilLocked(LOCK_USER, 'wrong-password-A')
    expect(locked).toBe(true)

    // 即便提供任意密码，锁定状态下登录都应失败
    const res = await attemptLogin(LOCK_USER, 'whatever')
    expect(res.success).toBe(false)
  })

  test('锁定后通过 UI 登录仍停留登录页且 token 为空（端到端阻断）', async ({ page }) => {
    const locked = await bruteForceUntilLocked(LOCK_USER, 'wrong-password-B')
    expect(locked).toBe(true)

    // 在浏览器中尝试登录被锁账号
    const captchaPromise = page.waitForResponse(
      (r) => r.url().includes('/api/sys/auth/captcha') && r.status() === 200,
    )
    await page.goto('/login')
    await page.getByPlaceholder('请输入用户名').waitFor({ timeout: 10_000 })
    const capJson: any = await (await captchaPromise).json()
    const code = await getCaptchaAnswer(capJson.data.captchaId)

    const userInput = page.getByPlaceholder('请输入用户名')
    await userInput.click()
    await userInput.press('ControlOrMeta+a')
    await userInput.pressSequentially(LOCK_USER, { delay: 10 })

    const passInput = page.getByPlaceholder('请输入密码')
    await passInput.click()
    await passInput.press('ControlOrMeta+a')
    await passInput.pressSequentially('any-password', { delay: 10 })

    const capInput = page.getByPlaceholder('请输入验证码')
    await capInput.click()
    await capInput.press('ControlOrMeta+a')
    await capInput.pressSequentially(code, { delay: 20 })

    await page.getByRole('button', { name: '登录' }).click()

    // 锁定阻断了登录：仍停留在登录页，未写入 token
    await expect(page).toHaveURL(/\/login/, { timeout: 10_000 })
    const token = await page.evaluate(() => localStorage.getItem('token'))
    expect(token).toBeNull()
  })

  test('锁定清除后，再次登录不再被锁定拦截（恢复闭环）', async () => {
    const locked = await bruteForceUntilLocked(LOCK_USER, 'wrong-password-for-recovery')
    expect(locked).toBe(true)

    const lockedRes = await attemptLogin(LOCK_USER, 'another-wrong-password')
    expect(lockedRes.success).toBe(false)
    expect(lockedRes.code).toBe(423)
    expect(lockedRes.message).toContain('已锁定')

    await redisDelete(LOCK_KEY)
    await redisDelete(FAIL_KEY)

    expect(await redisExists(LOCK_KEY)).toBe(false)

    const recoveredRes = await attemptLogin(LOCK_USER, 'yet-another-password')
    expect(recoveredRes.success).toBe(false)
    expect(recoveredRes.code).not.toBe(423)
    expect(recoveredRes.message).not.toContain('已锁定')
  })
})
