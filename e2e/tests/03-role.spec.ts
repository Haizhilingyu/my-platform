import { test, expect } from '@playwright/test'
import { loggedInPage } from '../fixtures/helpers'
import { apiLogin } from '../fixtures/auth'
import { queryOne, queryAll, execute } from '../fixtures/db'

const BASE = process.env.E2E_BASE_URL || 'http://localhost:8090'
const ROLE_CODE = 'e2e_role_1'

test.describe('角色管理 CRUD', () => {
  test.afterEach(async () => {
    await execute('DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE role_code LIKE $1)', ['e2e\\_%'])
    await execute('DELETE FROM sys_role WHERE role_code LIKE $1', ['e2e\\_%'])
  })

  test('角色列表加载', async ({ page }) => {
    await loggedInPage(page, '/sys/role')
    await expect(page.getByRole('button', { name: '新增角色' })).toBeVisible()
    await expect(page.getByText('角色编码').first()).toBeVisible({ timeout: 10_000 })
  })

  test('新增角色 → UI 成功 + DB 入库', async ({ page }) => {
    await loggedInPage(page, '/sys/role')
    await page.getByRole('button', { name: '新增角色' }).click()
    await expect(page.getByText('新增角色').first()).toBeVisible()

    const codeInput = page.getByPlaceholder('如 admin')
    await codeInput.click()
    await codeInput.pressSequentially(ROLE_CODE, { delay: 10 })

    const nameInput = page.getByPlaceholder('如 超级管理员')
    await nameInput.click()
    await nameInput.pressSequentially('E2E角色', { delay: 10 })

    await page.getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('新增成功')).toBeVisible({ timeout: 10_000 })

    const row = await queryOne('SELECT id, role_code, role_name FROM sys_role WHERE role_code = $1', [ROLE_CODE])
    expect(row).not.toBeNull()
    expect(row.role_name).toBe('E2E角色')
  })

  test('编辑角色 → DB 字段变更', async ({ page, request }) => {
    const token = await apiLogin()
    await request.post('/api/sys/role', {
      headers: { Authorization: `Bearer ${token}` },
      data: { roleCode: ROLE_CODE, roleName: 'before', dataScope: 'SELF' },
    })

    await loggedInPage(page, '/sys/role')
    await expect(page.getByText(ROLE_CODE).first()).toBeVisible({ timeout: 10_000 })
    const row = page.locator('tr', { hasText: ROLE_CODE }).first()
    await row.getByRole('button', { name: '编辑' }).click()
    await expect(page.getByText('编辑角色').first()).toBeVisible()

    const nameInput = page.getByPlaceholder('如 超级管理员')
    await nameInput.click()
    await nameInput.press('ControlOrMeta+a')
    await nameInput.pressSequentially('after-edit', { delay: 10 })

    await page.getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('修改成功')).toBeVisible({ timeout: 10_000 })

    const updated = await queryOne('SELECT role_name FROM sys_role WHERE role_code = $1', [ROLE_CODE])
    expect(updated.role_name).toBe('after-edit')
  })

  test('删除角色 → DB 行删除', async ({ page, request }) => {
    const token = await apiLogin()
    await request.post('/api/sys/role', {
      headers: { Authorization: `Bearer ${token}` },
      data: { roleCode: ROLE_CODE, roleName: 'to-delete', dataScope: 'SELF' },
    })

    await loggedInPage(page, '/sys/role')
    await expect(page.getByText(ROLE_CODE).first()).toBeVisible({ timeout: 10_000 })
    const row = page.locator('tr', { hasText: ROLE_CODE }).first()
    await row.getByRole('button', { name: '删除' }).click()
    await expect(page.getByText('删除成功')).toBeVisible({ timeout: 10_000 })

    const gone = await queryOne('SELECT id FROM sys_role WHERE role_code = $1', [ROLE_CODE])
    expect(gone).toBeNull()
  })

  test('分配菜单 → sys_role_menu 关联写入', async ({ page, request }) => {
    const token = await apiLogin()
    const roleRes = await request.post('/api/sys/role', {
      headers: { Authorization: `Bearer ${token}` },
      data: { roleCode: ROLE_CODE, roleName: 'perm-test', dataScope: 'SELF' },
    })
    const roleId = (await roleRes.json()).data

    const menus = await queryAll('SELECT id FROM sys_menu WHERE menu_type = $1 LIMIT 2', ['PAGE'])
    expect(menus.length).toBeGreaterThanOrEqual(1)
    const menuIds = menus.map((m) => m.id)

    await request.post(`/api/sys/role/${roleId}/menus`, {
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: menuIds,
    })

    const links = await queryAll('SELECT menu_id FROM sys_role_menu WHERE role_id = $1', [roleId])
    expect(links.map((r) => r.menu_id).sort()).toEqual(menuIds.sort())
  })
})
