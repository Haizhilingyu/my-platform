import { test, expect } from '@playwright/test'
import { loggedInPage } from '../fixtures/helpers'
import { apiLogin } from '../fixtures/auth'
import { queryOne, queryAll, execute } from '../fixtures/db'

const BASE = process.env.E2E_BASE_URL || 'http://localhost:8090'
const USERNAME = 'e2e_user'

test.describe('用户管理 CRUD', () => {
  test.afterEach(async () => {
    await execute('DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE $1)', ['e2e\\_%'])
    await execute('DELETE FROM sys_user WHERE username LIKE $1', ['e2e\\_%'])
  })

  test('用户列表加载 + admin 可见', async ({ page }) => {
    await loggedInPage(page, '/sys/user')
    await expect(page.getByPlaceholder('搜索用户名/姓名/电话')).toBeVisible()
    await expect(page.getByRole('button', { name: '新增用户' })).toBeVisible()
    await expect(page.getByText('admin').first()).toBeVisible({ timeout: 10_000 })
  })

  test('搜索关键字过滤结果', async ({ page }) => {
    await loggedInPage(page, '/sys/user')
    const search = page.getByPlaceholder('搜索用户名/姓名/电话')
    await search.click()
    await search.press('ControlOrMeta+a')
    await search.pressSequentially('admin', { delay: 10 })
    await page.getByRole('button', { name: '查询' }).click()
    await expect(page.getByText('admin').first()).toBeVisible({ timeout: 10_000 })
  })

  test('新增用户 → UI 成功 + DB 入库', async ({ page }) => {
    await loggedInPage(page, '/sys/user')
    await page.getByRole('button', { name: '新增用户' }).click()
    await expect(page.getByText('新增用户').first()).toBeVisible()

    await page.getByPlaceholder('请输入用户名').click()
    await page.getByPlaceholder('请输入用户名').pressSequentially(USERNAME, { delay: 10 })
    await page.getByPlaceholder('请输入密码').click()
    await page.getByPlaceholder('请输入密码').pressSequentially('Passw0rd!', { delay: 10 })
    await page.getByPlaceholder('请输入姓名').click()
    await page.getByPlaceholder('请输入姓名').pressSequentially('E2E用户', { delay: 10 })

    await page.getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('新增成功')).toBeVisible({ timeout: 10_000 })

    const row = await queryOne('SELECT id, username, real_name FROM sys_user WHERE username = $1', [USERNAME])
    expect(row).not.toBeNull()
    expect(row.username).toBe(USERNAME)
    expect(row.real_name).toBe('E2E用户')
  })

  test('编辑用户 → UI 成功 + DB 字段变更', async ({ page }) => {
    const token = await apiLogin()
    const createRes = await fetch(`${BASE}/api/sys/user`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: USERNAME, password: 'Passw0rd!', realName: 'before' }),
    })
    expect(createRes.status).toBe(200)

    await loggedInPage(page, '/sys/user')
    const search = page.getByPlaceholder('搜索用户名/姓名/电话')
    await search.click()
    await search.press('ControlOrMeta+a')
    await search.pressSequentially(USERNAME, { delay: 10 })
    await page.getByRole('button', { name: '查询' }).click()

    await expect(page.getByText(USERNAME).first()).toBeVisible({ timeout: 10_000 })
    const row = page.locator('tr', { hasText: USERNAME }).first()
    await row.getByRole('button', { name: '编辑' }).click()
    await expect(page.getByText('编辑用户').first()).toBeVisible()

    const realNameInput = page.getByPlaceholder('请输入姓名')
    await realNameInput.click()
    await realNameInput.press('ControlOrMeta+a')
    await realNameInput.pressSequentially('after-edit', { delay: 10 })

    await page.getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('修改成功')).toBeVisible({ timeout: 10_000 })

    const updated = await queryOne('SELECT real_name FROM sys_user WHERE username = $1', [USERNAME])
    expect(updated.real_name).toBe('after-edit')
  })

  test('删除用户 → UI 成功 + DB 行删除', async ({ page }) => {
    const token = await apiLogin()
    await fetch(`${BASE}/api/sys/user`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: USERNAME, password: 'Passw0rd!', realName: 'to-delete' }),
    })

    await loggedInPage(page, '/sys/user')
    const search = page.getByPlaceholder('搜索用户名/姓名/电话')
    await search.click()
    await search.press('ControlOrMeta+a')
    await search.pressSequentially(USERNAME, { delay: 10 })
    await page.getByRole('button', { name: '查询' }).click()

    await expect(page.getByText(USERNAME).first()).toBeVisible({ timeout: 10_000 })
    const row = page.locator('tr', { hasText: USERNAME }).first()
    await row.getByRole('button', { name: '删除' }).click()

    await expect(page.getByText('删除成功')).toBeVisible({ timeout: 10_000 })

    const gone = await queryOne('SELECT id FROM sys_user WHERE username = $1', [USERNAME])
    expect(gone).toBeNull()
  })

  test('重置密码 → DB 密码字段变更', async () => {
    const token = await apiLogin()
    await fetch(`${BASE}/api/sys/user`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: USERNAME, password: 'Passw0rd!', realName: 'reset-me' }),
    })
    const target = await queryOne('SELECT id, password FROM sys_user WHERE username = $1', [USERNAME])
    expect(target).not.toBeNull()
    const oldHash = target.password

    // 后端尚未在 UI 暴露重置密码按钮（user/index.vue 无），通过 API 验证
    const res = await fetch(`${BASE}/api/sys/user/${target.id}/reset-password?newPassword=NewPass1!`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(res.status).toBe(200)

    const after = await queryOne<{ password: string }>('SELECT password FROM sys_user WHERE id = $1', [target.id])
    expect(after!.password).not.toBe(oldHash)
    expect(after!.password.length).toBeGreaterThan(0)
  })

  test('分配角色 → sys_user_role 关联写入', async ({ page, request }) => {
    const token = await apiLogin()
    await request.post('/api/sys/user', {
      headers: { Authorization: `Bearer ${token}` },
      data: { username: USERNAME, password: 'Passw0rd!', realName: 'role-test' },
    })
    const roleRes = await request.post('/api/sys/role', {
      headers: { Authorization: `Bearer ${token}` },
      data: { roleCode: 'e2e_role_assign', roleName: 'E2E分配测试', dataScope: 'SELF' },
    })
    const roleId = (await roleRes.json()).data
    const userRow = await queryOne('SELECT id FROM sys_user WHERE username = $1', [USERNAME])

    await request.post(`/api/sys/user/${userRow.id}/roles`, {
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: [roleId],
    })

    const links = await queryAll('SELECT role_id FROM sys_user_role WHERE user_id = $1', [userRow.id])
    expect(links.map((r) => Number(r.role_id))).toContain(Number(roleId))
  })
})
