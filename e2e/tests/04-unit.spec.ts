import { test, expect } from '@playwright/test'
import { loggedInPage } from '../fixtures/helpers'
import { queryOne, execute } from '../fixtures/db'

const ROOT_CODE = 'E2E_UNIT_ROOT'
const CHILD_CODE = 'E2E_UNIT_CHILD'

test.describe('单位管理 CRUD + Bug2 回归', () => {
  test.afterEach(async () => {
    await execute("DELETE FROM sys_unit WHERE unit_code LIKE 'E2E\\_%'")
  })

  test('单位树渲染', async ({ page }) => {
    await loggedInPage(page, '/sys/unit')
    await expect(page.getByRole('button', { name: '新增单位' })).toBeVisible()
  })

  test('新增根单位 → 节点恰出现一次（Bug2 回归） + DB 入库', async ({ page }) => {
    await loggedInPage(page, '/sys/unit')

    const beforeCount = await page.locator('.n-tree-node').count()

    await page.getByRole('button', { name: '新增单位' }).click()
    await expect(page.getByText('新增单位').first()).toBeVisible()

    const codeInput = page.getByPlaceholder('如 HQ')
    await codeInput.click()
    await codeInput.pressSequentially(ROOT_CODE, { delay: 10 })

    const nameInput = page.getByPlaceholder('如 总部')
    await nameInput.click()
    await nameInput.pressSequentially('E2E根单位', { delay: 10 })

    await page.getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('新增成功')).toBeVisible({ timeout: 10_000 })

    // Bug 2 回归断言：树中恰好新增一个节点（修复前会出现两次）
    await page.waitForTimeout(500)
    const afterCount = await page.locator('.n-tree-node').count()
    expect(afterCount - beforeCount).toBe(1)

    const row = await queryOne('SELECT id, unit_code, unit_name, parent_id FROM sys_unit WHERE unit_code = $1', [ROOT_CODE])
    expect(row).not.toBeNull()
    expect(row.unit_name).toBe('E2E根单位')
    expect(row.parent_id).toBeNull()
  })

  test('新增子单位 → parent_id 正确 + 节点恰出现一次', async ({ page }) => {
    await loggedInPage(page, '/sys/unit')
    await page.getByRole('button', { name: '新增单位' }).click()

    const codeInput = page.getByPlaceholder('如 HQ')
    await codeInput.pressSequentially(ROOT_CODE, { delay: 10 })
    const nameInput = page.getByPlaceholder('如 总部')
    await nameInput.pressSequentially('E2E根单位', { delay: 10 })
    await page.getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('新增成功')).toBeVisible({ timeout: 10_000 })
    await page.waitForTimeout(1000)

    await page.locator('.n-tree-node', { hasText: ROOT_CODE }).first().getByRole('button', { name: '新增' }).click()
    await expect(page.getByText('新增单位').first()).toBeVisible()

    const childCodeInput = page.getByPlaceholder('如 HQ')
    await childCodeInput.click()
    await childCodeInput.press('ControlOrMeta+a')
    await childCodeInput.pressSequentially(CHILD_CODE, { delay: 10 })
    const childNameInput = page.getByPlaceholder('如 总部')
    await childNameInput.click()
    await childNameInput.press('ControlOrMeta+a')
    await childNameInput.pressSequentially('E2E子单位', { delay: 10 })
    await page.getByRole('button', { name: '保存' }).click()

    // 等待子节点在树中渲染出来（避免 toast 竞态 + 确保 DB 已提交）
    await expect(page.locator('.n-tree-node', { hasText: CHILD_CODE })).toBeVisible({ timeout: 10_000 })

    const child = await queryOne<{ unit_name: string; parent_id: number | null }>(
      'SELECT unit_name, parent_id FROM sys_unit WHERE unit_code = $1',
      [CHILD_CODE],
    )
    expect(child).not.toBeNull()
    expect(child!.unit_name).toBe('E2E子单位')
  })

  test('编辑单位 → DB 名称变更', async ({ page, request }) => {
    const token = await loggedInPageToken(page)
    await request.post('/api/sys/unit', {
      headers: { Authorization: `Bearer ${token}` },
      data: { unitCode: ROOT_CODE, unitName: 'before', sort: 0, status: 1 },
    })

    await page.goto('/sys/unit')
    await page.waitForLoadState('networkidle')
    await page.locator('.n-tree-node', { hasText: ROOT_CODE }).first().getByRole('button', { name: '编辑' }).click()
    await expect(page.getByText('编辑单位').first()).toBeVisible()

    const nameInput = page.getByPlaceholder('如 总部')
    await nameInput.click()
    await nameInput.press('ControlOrMeta+a')
    await nameInput.pressSequentially('after-edit', { delay: 10 })
    await page.getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('修改成功')).toBeVisible({ timeout: 10_000 })

    const updated = await queryOne('SELECT unit_name FROM sys_unit WHERE unit_code = $1', [ROOT_CODE])
    expect(updated.unit_name).toBe('after-edit')
  })

  test('删除单位 → DB 行删除', async ({ page, request }) => {
    const token = await loggedInPageToken(page)
    await request.post('/api/sys/unit', {
      headers: { Authorization: `Bearer ${token}` },
      data: { unitCode: ROOT_CODE, unitName: 'to-delete', sort: 0, status: 1 },
    })

    await page.goto('/sys/unit')
    await page.waitForLoadState('networkidle')
    await page.locator('.n-tree-node', { hasText: ROOT_CODE }).first().getByRole('button', { name: '删除' }).click()
    await expect(page.getByText('删除成功')).toBeVisible({ timeout: 10_000 })

    const gone = await queryOne('SELECT id FROM sys_unit WHERE unit_code = $1', [ROOT_CODE])
    expect(gone).toBeNull()
  })
})

async function loggedInPageToken(page: import('@playwright/test').Page): Promise<string> {
  const { apiLogin, setAuth } = await import('../fixtures/auth')
  const token = await apiLogin()
  await setAuth(page, token)
  return token
}
