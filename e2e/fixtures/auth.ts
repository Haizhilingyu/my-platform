import type { Page } from '@playwright/test'
import { getCaptchaAnswer } from './redis'

const BASE = process.env.E2E_BASE_URL || 'http://localhost:8090'

export const AUTH = {
  admin: { username: 'admin', password: 'admin123' },
}

/**
 * API 登录：调用 /api/sys/auth/captcha 拿到 captchaId，从 Redis 读取答案，
 * 然后 POST /api/sys/auth/login，返回 JWT token。仅需要鉴权的测试用此快速通道。
 */
export async function apiLogin(username = 'admin', password = 'admin123'): Promise<string> {
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
  const loginJson: any = await loginRes.json()
  if (!loginJson.success) throw new Error(`Login failed: ${loginJson.message}`)
  return loginJson.data.token as string
}

/** 提取 JWT 中的 jti（用于后续 Redis 会话键断言） */
export function extractJti(token: string): string {
  const parts = token.split('.')
  if (parts.length !== 3) throw new Error('Not a JWT')
  const payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'))
  if (!payload.jti) throw new Error('Token has no jti claim')
  return payload.jti as string
}

/**
 * UI 登录：导航到 /login，等待 Login.vue 自动拉取验证码，从响应中捕获 captchaId，
 * 从 Redis 解出答案填入表单，提交后等待进入 /dashboard。
 *
 * 必须在 page.goto 之前注册 waitForResponse —— onMounted 触发的 captcha 请求与
 * 导航并发，监听器若晚于 goto 注册就会错过响应。
 */
export async function uiLogin(page: Page, username = 'admin', password = 'admin123'): Promise<void> {
  // 监听器先注册，避免与 page.goto 的竞态
  const captchaPromise = page.waitForResponse(
    (r) => r.url().includes('/api/sys/auth/captcha') && r.status() === 200,
  )
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名').waitFor({ timeout: 10_000 })
  const captchaResp = await captchaPromise
  const capJson: any = await captchaResp.json()
  const captchaId = capJson.data.captchaId
  const captchaCode = await getCaptchaAnswer(captchaId)

  // Naive UI 受控输入：fill 会被 v-model 回滚，必须 pressSequentially 逐字符触发
  const userInput = page.getByPlaceholder('请输入用户名')
  const passInput = page.getByPlaceholder('请输入密码')
  await userInput.click()
  await userInput.press('ControlOrMeta+a')
  await userInput.pressSequentially(username, { delay: 10 })
  await passInput.click()
  await passInput.press('ControlOrMeta+a')
  await passInput.pressSequentially(password, { delay: 10 })

  const captchaInput = page.getByPlaceholder('请输入验证码')
  if (await captchaInput.isVisible({ timeout: 3000 }).catch(() => false)) {
    await captchaInput.click()
    await captchaInput.press('ControlOrMeta+a')
    await captchaInput.pressSequentially(captchaCode, { delay: 20 })
  }

  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL(/\/dashboard/, { timeout: 15_000 })
}

/**
 * 直接设置 localStorage 的 token，省去 UI 登录流程。
 * 用法：在已登录场景下复用 token，跳过登录页。
 */
export async function setAuth(page: Page, token: string): Promise<void> {
  await page.goto('/')
  await page.evaluate((t) => localStorage.setItem('token', t), token)
}
