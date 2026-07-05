import { test, expect } from '@playwright/test'
import { loggedInPage } from '../fixtures/helpers'

test.describe('外部应用管理 (OAuth2 clients)', () => {
  test('应用列表页正确渲染', async ({ page }) => {
    await loggedInPage(page, '/sys/app')
    await expect(page.getByPlaceholder('搜索 Client ID / 应用名称')).toBeVisible()
    await expect(page.getByRole('button', { name: '查询' })).toBeVisible()
    await expect(page.getByRole('button', { name: '新增应用' })).toBeVisible()
  })

  test('点击新增应用打开创建表单弹窗', async ({ page }) => {
    await loggedInPage(page, '/sys/app')
    await page.getByRole('button', { name: '新增应用' }).click()

    await expect(page.getByText('新增应用').first()).toBeVisible({ timeout: 5_000 })
    await expect(page.getByText('应用名称').first()).toBeVisible()
    await expect(page.getByText('回调地址').first()).toBeVisible()
    await expect(page.getByText('授权范围').first()).toBeVisible()
    await expect(page.getByText('授权类型').first()).toBeVisible()
  })

  test('填写表单并提交创建客户端', async ({ page }) => {
    await loggedInPage(page, '/sys/app')
    await page.getByRole('button', { name: '新增应用' }).click()
    await expect(page.getByText('新增应用').first()).toBeVisible()

    const appName = `e2e-app-${Date.now()}`
    const nameInput = page.getByPlaceholder('如：移动端 App')
    await nameInput.click()
    await nameInput.pressSequentially(appName, { delay: 10 })

    // 回调地址是动态列表 —— 需先点「添加回调地址」生成输入框
    await page.getByRole('button', { name: '添加回调地址' }).click()
    const callbackInput = page.getByPlaceholder('https://example.com/callback')
    await callbackInput.click()
    await callbackInput.pressSequentially('http://127.0.0.1:18080/callback', { delay: 5 })

    await page.getByRole('button', { name: '保存' }).click()

    const ok = await page
      .getByText('操作成功')
      .or(page.getByText('新增成功'))
      .or(page.getByText(/Client Secret|客户端密钥/))
      .first()
      .waitFor({ timeout: 10_000 })
      .then(() => true)
      .catch(() => false)
    expect(ok).toBe(true)
  })
})
