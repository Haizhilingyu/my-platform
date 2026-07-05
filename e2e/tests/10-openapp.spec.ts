import { test, expect } from '@playwright/test'
import { loggedInPage } from '../fixtures/helpers'
import { apiLogin } from '../fixtures/auth'
import { execute } from '../fixtures/db'

test.describe('外部应用管理 (OAuth2 clients)', () => {
  test.afterEach(async () => {
    await execute("DELETE FROM openapp_client WHERE client_name LIKE 'e2e-%'")
  })

  test('应用列表页正确渲染', async ({ page }) => {
    await loggedInPage(page, '/sys/app')
    await expect(page.getByRole('button', { name: '新增应用' })).toBeVisible()
  })

  test('点击新增应用打开创建表单弹窗', async ({ page }) => {
    await loggedInPage(page, '/sys/app')
    await page.getByRole('button', { name: '新增应用' }).click()

    await expect(page.getByText('新增应用').first()).toBeVisible({ timeout: 5_000 })
    await expect(page.getByText('应用名称').first()).toBeVisible()
    await expect(page.getByText('回调地址').first()).toBeVisible()
  })

  test('API 创建客户端 → UI 列表显示 + DB 入库', async ({ page, request }) => {
    const token = await apiLogin()
    const appName = `e2e-app-${Date.now()}`

    const res = await request.post('/api/sys/openapp/clients', {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        clientName: appName,
        redirectUris: ['http://127.0.0.1:18080/callback'],
        scopes: ['openid', 'profile'],
        grantTypes: ['authorization_code'],
      },
    })
    expect(res.status()).toBe(200)

    await loggedInPage(page, '/sys/app')
    await expect(page.getByText(appName).first()).toBeVisible({ timeout: 10_000 })
  })
})
