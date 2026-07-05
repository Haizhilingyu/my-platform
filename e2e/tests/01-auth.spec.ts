import { test, expect } from '@playwright/test'
import { uiLogin, extractJti } from '../fixtures/auth'
import { redisExists } from '../fixtures/redis'

const USERNAME_PH = '请输入用户名'
const PASSWORD_PH = '请输入密码'
const LOGIN_BTN = '登录'
const DISPLAY_NAME = '超级管理员'

test.describe('认证流程', () => {
  test('登录页正确渲染', async ({ page }) => {
    await page.goto('/login')
    await expect(page.getByPlaceholder(USERNAME_PH)).toBeVisible()
    await expect(page.getByPlaceholder(PASSWORD_PH)).toBeVisible()
    await expect(page.getByPlaceholder('请输入验证码')).toBeVisible()
    await expect(page.getByRole('button', { name: LOGIN_BTN })).toBeVisible()
  })

  test('路由守卫：未登录访问受保护页应重定向到 /login', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveURL(/\/login/)
  })

  test('错误密码登录应被拒绝并提示', async ({ page }) => {
    // 监听器先注册，避免与 page.goto 的竞态（同 uiLogin）
    const { getCaptchaAnswer } = await import('../fixtures/redis')
    const captchaPromise = page.waitForResponse(
      (r) => r.url().includes('/api/sys/auth/captcha') && r.status() === 200,
    )
    await page.goto('/login')
    await page.getByPlaceholder(USERNAME_PH).waitFor({ timeout: 10_000 })
    const captchaResp = await captchaPromise
    const capJson: any = await captchaResp.json()
    const captchaCode = await getCaptchaAnswer(capJson.data.captchaId)

    const userInput = page.getByPlaceholder(USERNAME_PH)
    const pwd = page.getByPlaceholder(PASSWORD_PH)
    await userInput.click()
    await userInput.press('ControlOrMeta+a')
    await userInput.pressSequentially('admin', { delay: 10 })
    await pwd.click()
    await pwd.press('ControlOrMeta+a')
    await pwd.pressSequentially('wrong-password', { delay: 15 })

    const captchaInput = page.getByPlaceholder('请输入验证码')
    if (await captchaInput.isVisible({ timeout: 3000 }).catch(() => false)) {
      await captchaInput.click()
      await captchaInput.press('ControlOrMeta+a')
      await captchaInput.pressSequentially(captchaCode, { delay: 15 })
    }

    await page.getByRole('button', { name: LOGIN_BTN }).click()

    await expect(page.getByText(/用户名或密码错误|验证码错误|登录失败/).first()).toBeVisible({ timeout: 10_000 })
    await expect(page).toHaveURL(/\/login/)
    const token = await page.evaluate(() => localStorage.getItem('token'))
    expect(token).toBeNull()
  })

  test('正确登录后进入主页 + token 写入 localStorage + Redis 活跃会话存在', async ({ page }) => {
    await uiLogin(page)

    await expect(page).toHaveURL(/\/dashboard/)
    await expect(page.getByText(DISPLAY_NAME).first()).toBeVisible()

    const token = await page.evaluate(() => localStorage.getItem('token'))
    expect(token).toBeTruthy()

    // Bug 1 相关：登录后 Redis 必须存在 session:active:{jti}
    const jti = extractJti(token!)
    expect(await redisExists(`session:active:${jti}`)).toBe(true)
  })

  test('登录后可导航到子页', async ({ page }) => {
    await uiLogin(page)
    await page.getByText('系统管理').first().click()
    await page.getByText('用户管理').first().click()
    await expect(page).toHaveURL(/\/sys\/user/)
  })

  test('退出登录后回到登录页并清空 token', async ({ page }) => {
    await uiLogin(page)
    await page.getByText(DISPLAY_NAME).first().click()
    await page.getByText('退出登录').click()
    await expect(page).toHaveURL(/\/login/)
    const token = await page.evaluate(() => localStorage.getItem('token'))
    expect(token).toBeNull()
  })
})
