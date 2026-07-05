import { test, expect } from '@playwright/test'
import { loggedInPage } from '../fixtures/helpers'
import { apiLogin } from '../fixtures/auth'
import { queryOne, execute } from '../fixtures/db'

const CONFIG_KEY = 'e2e.test.value'

test.describe('系统配置', () => {
  test.afterEach(async () => {
    await execute("DELETE FROM sys_config WHERE config_key LIKE 'e2e.%'")
  })

  test('配置列表加载', async ({ page }) => {
    await loggedInPage(page, '/sys/config')
    await expect(page.getByRole('button', { name: '新增配置' })).toBeVisible()
    await expect(page.getByText('配置键').first()).toBeVisible({ timeout: 10_000 })
  })

  test('新增配置 → DB 入库', async ({ page }) => {
    await loggedInPage(page, '/sys/config')
    await page.getByRole('button', { name: '新增配置' }).click()
    await expect(page.getByText('新增配置').first()).toBeVisible()

    const keyInput = page.getByPlaceholder('sys.password.min-length')
    await keyInput.click()
    await keyInput.pressSequentially(CONFIG_KEY, { delay: 10 })

    const valueInput = page.getByPlaceholder('配置值')
    await valueInput.click()
    await valueInput.pressSequentially('before-value', { delay: 10 })

    await page.getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('新增成功')).toBeVisible({ timeout: 10_000 })

    const row = await queryOne('SELECT config_value, category FROM sys_config WHERE config_key = $1', [CONFIG_KEY])
    expect(row).not.toBeNull()
    expect(row.config_value).toBe('before-value')
  })

  test('编辑配置 → DB 值变更', async ({ page, request }) => {
    const token = await apiLogin()
    await request.post('/api/sys/config', {
      headers: { Authorization: `Bearer ${token}` },
      data: { configKey: CONFIG_KEY, configValue: 'before', configType: 'STRING', category: 'default' },
    })

    await loggedInPage(page, '/sys/config')
    await expect(page.getByText(CONFIG_KEY).first()).toBeVisible({ timeout: 10_000 })
    const row = page.locator('tr', { hasText: CONFIG_KEY }).first()
    await row.getByRole('button', { name: '编辑' }).click()
    await expect(page.getByText('编辑配置').first()).toBeVisible()

    const valueInput = page.getByPlaceholder('配置值')
    await valueInput.click()
    await valueInput.press('ControlOrMeta+a')
    await valueInput.pressSequentially('after-value', { delay: 10 })

    await page.getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('修改成功')).toBeVisible({ timeout: 10_000 })

    const updated = await queryOne('SELECT config_value FROM sys_config WHERE config_key = $1', [CONFIG_KEY])
    expect(updated.config_value).toBe('after-value')
  })
})
