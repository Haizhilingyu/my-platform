import { test, expect } from '@playwright/test'
import { loggedInPage } from '../fixtures/helpers'
import { queryOne, execute } from '../fixtures/db'


/**
 * 菜单管理 E2E。
 *
 * 业务约束：菜单由系统初始化（Flyway 种子）写入，UI 仅支持编辑展示信息（名称/图标/排序/可见性），
 * 不提供新增/删除入口。因此本测试只覆盖：
 *   - 种子菜单树渲染（断言已知菜单节点可见）
 *   - 编辑流程（编辑种子菜单「系统配置」的名称 → DB 验证 → 恢复原名，不污染种子数据）
 *
 * 不走 API 创建临时菜单：sys_menu 的 IDENTITY 序列在多轮 E2E 后会与 MAX(id) 失准
 * （V99 setval 仅在首次迁移时同步），API 创建会撞主键 500。
 */
const TARGET_PERM = 'sys:config:list' // 种子菜单「系统配置」
const ORIGINAL_NAME = '系统配置'
const EDITED_NAME = 'E2E编辑中'

test.describe('菜单管理', () => {
  test.afterEach(async () => {
    // 恢复种子菜单原名，避免污染
    await execute('UPDATE sys_menu SET menu_name = $1 WHERE permission = $2', [ORIGINAL_NAME, TARGET_PERM])
  })
  test('种子菜单树渲染', async ({ page }) => {
    await loggedInPage(page, '/sys/menu')
    await expect(page.locator('.n-tree-node').first()).toBeVisible({ timeout: 15_000 })
    await expect(page.getByText('用户管理').first()).toBeVisible({ timeout: 10_000 })
  })

  test('编辑菜单 → DB 名称变更 + 恢复', async ({ page }) => {
    await loggedInPage(page, '/sys/menu')
    const node = page.locator('.n-tree-node', { hasText: ORIGINAL_NAME }).first()
    await node.hover()
    await node.getByRole('button', { name: '编辑' }).click()
    await expect(page.getByText('编辑菜单').first()).toBeVisible({ timeout: 15_000 })

    const nameInput = page.getByPlaceholder('菜单名称')
    await nameInput.click()
    await nameInput.press('ControlOrMeta+a')
    await nameInput.pressSequentially(EDITED_NAME, { delay: 10 })

    await page.getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('修改成功')).toBeVisible({ timeout: 15_000 })

    const updated = await queryOne('SELECT menu_name FROM sys_menu WHERE permission = $1', [TARGET_PERM])
    expect(updated.menu_name).toBe(EDITED_NAME)
  })
})
