import { test, expect } from '@playwright/test'
import { getCaptchaAnswer, redisExists, redisDelete, getRedis } from '../fixtures/redis'

/**
 * 账号锁定 —— 管理员豁免端到端测试
 *
 * 与 12-lockout.spec.ts 形成对称覆盖：
 *   - 12 验证「普通账号连续错误密码 → 被锁定」
 *   - 本文件验证「管理员(admin)连续错误密码 → 不被计数、不被锁定」
 *
 * 依据：LoginSecurityService.recordFailedAttempt 对 isAdminExempt(username)
 * 直接返回，不写入 login:fail:{username}，也不写 user:lock:{username}。
 * ADMIN_USERNAME 固定为 "admin"（忽略大小写）。
 *
 * 注意：本 spec 故意用错误密码打 admin，但 afterEach 会清理可能残留的
 * lock/fail key，且本文件在套件最后执行，不会污染其他用例。
 */

const BASE = process.env.E2E_BASE_URL || 'http://localhost:8090'
const ADMIN = 'admin'
const ADMIN_UPPER = 'ADMIN'
const ADMIN_TITLE = 'Admin'
const FAIL_KEY = `login:fail:${ADMIN}`
const LOCK_KEY = `user:lock:${ADMIN}`
const FAIL_KEY_UPPER = `login:fail:${ADMIN_UPPER}`
const LOCK_KEY_UPPER = `user:lock:${ADMIN_UPPER}`
const FAIL_KEY_TITLE = `login:fail:${ADMIN_TITLE}`
const LOCK_KEY_TITLE = `user:lock:${ADMIN_TITLE}`

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

test.describe('账号锁定 - 管理员豁免', () => {
  test.afterEach(async () => {
    await redisDelete(LOCK_KEY)
    await redisDelete(FAIL_KEY)
    await redisDelete(LOCK_KEY_UPPER)
    await redisDelete(FAIL_KEY_UPPER)
    await redisDelete(LOCK_KEY_TITLE)
    await redisDelete(FAIL_KEY_TITLE)
  })

  test('管理员连续错误密码不计入失败、不被锁定', async () => {
    // 错误次数远超默认阈值(3)，若豁免失效则 admin 会被锁定并破坏整套 E2E
    for (let i = 0; i < 5; i++) {
      await attemptLogin(ADMIN, 'wrong-password-for-admin-attempt')
    }

    // 核心断言：管理员豁免生效 —— 失败计数 key 不存在、锁定 key 不存在
    expect(await redisExists(LOCK_KEY)).toBe(false)

    const r = getRedis()
    const failCount = parseInt((await r.get(FAIL_KEY)) || '0', 10)
    expect(failCount).toBe(0)
  })

  test('管理员豁免后仍能正常登录（不受错误尝试影响）', async () => {
    for (let i = 0; i < 4; i++) {
      await attemptLogin(ADMIN, 'wrong-before-real-login')
    }

    const capRes = await fetch(`${BASE}/api/sys/auth/captcha`)
    const capJson: any = await capRes.json()
    const captchaCode = await getCaptchaAnswer(capJson.data.captchaId)
    const loginRes = await fetch(`${BASE}/api/sys/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: ADMIN, password: 'admin123', captchaId: capJson.data.captchaId, captchaCode }),
    })
    const loginJson: any = await loginRes.json()
    expect(loginJson.success).toBe(true)
    expect(loginJson.data.token).toBeTruthy()
  })

  test('管理员用户名大小写变体（Admin/ADMIN）同样豁免不被锁定', async () => {
    for (let i = 0; i < 5; i++) {
      await attemptLogin(ADMIN_TITLE, 'wrong-password-title-case')
    }

    expect(await redisExists(LOCK_KEY_TITLE)).toBe(false)
    expect(await redisExists(LOCK_KEY)).toBe(false)

    for (let i = 0; i < 5; i++) {
      await attemptLogin(ADMIN_UPPER, 'wrong-password-upper-case')
    }

    expect(await redisExists(LOCK_KEY_UPPER)).toBe(false)
    expect(await redisExists(LOCK_KEY)).toBe(false)
    expect(await redisExists(LOCK_KEY_TITLE)).toBe(false)

    expect(await redisExists(FAIL_KEY_UPPER)).toBe(false)
    expect(await redisExists(FAIL_KEY_TITLE)).toBe(false)
  })
})
