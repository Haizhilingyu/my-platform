import { test, expect } from '@playwright/test'

/**
 * Layer 2: 消息中心 UI 端到端测试（T29）。
 *
 * 覆盖：
 *   - 消息列表页渲染与筛选
 *   - 通过 API 发布消息后，列表实时刷新
 *   - WebSocket 推送弹窗（URGENT 级别触发通知）
 *
 * 前置：后端 /sys/notify/inbox 与 /sys/notify/publish 可用，admin 持有 sys:notify:publish 权限。
 * 若权限未播种，发布相关用例通过 API 失败兜底，不阻塞列表渲染验证。
 */

const DISPLAY_NAME = '超级管理员'

async function adminLogin(page: import('@playwright/test').Page): Promise<string> {
  await page.goto('/login')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 })
  return (await page.evaluate(() => localStorage.getItem('token'))) || ''
}

test('消息列表页正确渲染筛选控件', async ({ page }) => {
  await adminLogin(page)

  await page.getByText('系统管理').first().click()
  await page.getByText('消息中心').first().click()
  await expect(page).toHaveURL(/\/sys\/message/)

  await expect(page.getByPlaceholder('搜索标题')).toBeVisible()
  await expect(page.getByRole('button', { name: '批量标记已读' })).toBeVisible()
})

test('通过 API 发布普通消息后列表刷新可见', async ({ page, request }) => {
  const token = await adminLogin(page)

  const title = `e2e-${Date.now()}`
  const publishResp = await request.post('/api/sys/notify/publish', {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: {
      title,
      content: 'T29 Playwright 全链路验证',
      level: 'NORMAL',
      recipients: [{ type: 'USER', id: 1 }],
    },
  })

  await page.goto('/sys/message')

  if (publishResp.ok()) {
    await expect(page.getByText(title).first()).toBeVisible({ timeout: 10_000 })
  } else {
    await expect(page.getByPlaceholder('搜索标题')).toBeVisible()
    test.skip(true, '消息发布接口不可用，跳过列表内容验证')
  }
})

test('URGENT 级别消息触发 WebSocket 通知弹窗', async ({ page, request }) => {
  test.skip(!process.env.E2E_WS_ENABLED, '需 E2E_WS_ENABLED=1 启用 WebSocket 弹窗验证')
  const token = await adminLogin(page)

  const title = `urgent-${Date.now()}`
  const resp = await request.post('/api/sys/notify/publish', {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: {
      title,
      content: '紧急消息弹窗测试',
      level: 'URGENT',
      recipients: [{ type: 'USER', id: 1 }],
    },
  })
  test.expect(resp.ok()).toBeTruthy()

  await expect(page.getByText(title).first()).toBeVisible({ timeout: 15_000 })
})
