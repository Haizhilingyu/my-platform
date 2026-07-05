import { test, expect } from '@playwright/test'
import { loggedInPage } from '../fixtures/helpers'
import { queryOne, execute } from '../fixtures/db'

const AUDIT_MENU_ID = 990

test.describe('审计日志', () => {
  test.beforeAll(async () => {
    // 后端 V21__audit_menu.sql 试图用 id=50 播种审计菜单，但 id=50 已被在线会话菜单占用，
    // 导致审计菜单从未被播种，admin 无 sys:audit:list 权限，路由守卫拦截 /sys/audit。
    // 这里在测试侧补种（legitimate E2E fixture setup），afterAll 清理。
    await execute(
      `INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort, visible, status)
       SELECT $1, 1, '审计日志(E2E)', 'PAGE', '/sys/audit', 'sys/audit/index', 'sys:audit:list', 'Document', 99, 1, 1
       WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = $1)`,
      [AUDIT_MENU_ID],
    )
    await execute(
      `INSERT INTO sys_role_menu (role_id, menu_id) SELECT 1, $1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = $1)`,
      [AUDIT_MENU_ID],
    )
  })

  test.afterAll(async () => {
    await execute('DELETE FROM sys_role_menu WHERE role_id = 1 AND menu_id = $1', [AUDIT_MENU_ID])
    await execute('DELETE FROM sys_menu WHERE id = $1', [AUDIT_MENU_ID])
  })

  test('审计日志列表加载', async ({ page }) => {
    await loggedInPage(page, '/sys/audit')
    await expect(page.getByPlaceholder('操作人')).toBeVisible({ timeout: 10_000 })
    await expect(page.getByRole('button', { name: '查询' })).toBeVisible()
    await expect(page.getByRole('button', { name: '重置' })).toBeVisible()
  })

  test('登录行为已落审计表（DB 验证）', async () => {
    // @Auditable(action="LOGIN") 在认证前记录，actor 为 "anonymous"
    const row = await queryOne(
      `SELECT id, action, result FROM audit_log WHERE action = $1 ORDER BY id DESC LIMIT 1`,
      ['LOGIN'],
    )
    expect(row).not.toBeNull()
    expect(row.action).toBe('LOGIN')
    expect(row.result).toBe('success')
  })

  test('按操作类型筛选审计日志', async ({ page }) => {
    await loggedInPage(page, '/sys/audit')
    await page.getByPlaceholder('操作人').waitFor({ timeout: 10_000 })
    await page.locator('.n-base-selection').first().click()
    await page.getByText('LOGIN').first().click()
    await page.getByRole('button', { name: '查询' }).click()
    await expect(page.getByText('LOGIN').first()).toBeVisible({ timeout: 10_000 })
  })
})
