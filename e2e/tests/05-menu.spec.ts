import { test, expect } from '@playwright/test'
import { loggedInPage } from '../fixtures/helpers'
import { apiLogin } from '../fixtures/auth'
import { queryOne, execute } from '../fixtures/db'

const MENU_NAME = 'E2E测试菜单'
const PERM = 'e2e:test:view'

test.describe('菜单管理 CRUD', () => {
  test.afterEach(async () => {
    await execute("DELETE FROM sys_menu WHERE menu_name LIKE 'E2E%' OR permission LIKE 'e2e:%'")
  })

  test('菜单树渲染', async ({ page }) => {
    await loggedInPage(page, '/sys/menu')
    await expect(page.getByRole('button', { name: '新增菜单' })).toBeVisible()
  })

  test('新增菜单 → UI 成功 + DB 入库', async ({ page }) => {
    await loggedInPage(page, '/sys/menu')

    const beforeCount = await page.locator('.n-tree-node-content').count()

    await page.getByRole('button', { name: '新增菜单' }).click()
    await expect(page.getByRole('heading', { name: '新增菜单' })).toBeVisible()

    await page.getByPlaceholder('菜单名称').fill(MENU_NAME)
    await page.getByPlaceholder('/sys/user').fill('/e2e/test')
    await page.getByPlaceholder('sys:user:add').fill(PERM)
    await page.waitForTimeout(200)

    await page.getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('新增成功')).toBeVisible({ timeout: 10_000 })

    const row = await queryOne('SELECT id, menu_name, permission, menu_type FROM sys_menu WHERE permission = $1', [PERM])
    expect(row).not.toBeNull()
    expect(row.menu_name).toBe(MENU_NAME)
  })

  test('编辑菜单 → DB 名称变更', async ({ page, request }) => {
    const token = await apiLogin()
    await request.post('/api/sys/menu', {
      headers: { Authorization: `Bearer ${token}` },
      data: { menuName: MENU_NAME, menuType: 'PAGE', path: '/e2e/test', permission: PERM, sort: 0, visible: 1, status: 1 },
    })

    await loggedInPage(page, '/sys/menu')
    await page.locator('.n-tree-node-content', { hasText: MENU_NAME }).first().locator('..').getByRole('button', { name: '编辑' }).click()
    await expect(page.getByText('编辑菜单').first()).toBeVisible()

    const nameInput = page.getByPlaceholder('菜单名称')
    await nameInput.click()
    await nameInput.press('ControlOrMeta+a')
    await nameInput.pressSequentially('E2E已编辑', { delay: 10 })

    await page.getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('修改成功')).toBeVisible({ timeout: 10_000 })

    const updated = await queryOne('SELECT menu_name FROM sys_menu WHERE permission = $1', [PERM])
    expect(updated.menu_name).toBe('E2E已编辑')
  })

  test('删除菜单 → DB 行删除', async ({ page, request }) => {
    const token = await apiLogin()
    await request.post('/api/sys/menu', {
      headers: { Authorization: `Bearer ${token}` },
      data: { menuName: MENU_NAME, menuType: 'PAGE', path: '/e2e/test', permission: PERM, sort: 0, visible: 1, status: 1 },
    })

    await loggedInPage(page, '/sys/menu')
    await page.locator('.n-tree-node-content', { hasText: MENU_NAME }).first().locator('..').getByRole('button', { name: '删除' }).click()
    await expect(page.getByText('删除成功')).toBeVisible({ timeout: 10_000 })

    const gone = await queryOne('SELECT id FROM sys_menu WHERE permission = $1', [PERM])
    expect(gone).toBeNull()
  })
})
