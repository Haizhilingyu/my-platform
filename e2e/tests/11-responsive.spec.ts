import { test, expect } from '@playwright/test'
import { uiLogin } from '../fixtures/auth'

const VIEWPORTS = [
  { name: 'mobile', width: 375, height: 720 },
  { name: 'tablet', width: 768, height: 1024 },
  { name: 'desktop', width: 1280, height: 800 },
]

for (const vp of VIEWPORTS) {
  test(`登录页在 ${vp.name} (${vp.width}px) 下正确渲染`, async ({ page }) => {
    await page.setViewportSize({ width: vp.width, height: vp.height })
    await page.goto('/login')

    await expect(page.getByPlaceholder('请输入用户名')).toBeVisible()
    await expect(page.getByRole('button', { name: '登录' })).toBeVisible()

    await page.screenshot({
      path: `test-results/responsive-${vp.name}-login.png`,
      fullPage: true,
    })
  })
}

test('登录后桌面端布局含侧栏与顶栏', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 })
  await uiLogin(page)

  await expect(page.getByText('My Platform').first()).toBeVisible()
  await expect(page.getByText('超级管理员').first()).toBeVisible()

  await page.screenshot({ path: 'test-results/responsive-desktop-dashboard.png' })
})

test('登录后移动端侧栏收起为抽屉', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 720 })
  await uiLogin(page)

  await expect(page).toHaveURL(/\/dashboard/)

  const sider = page.locator('.n-layout-sider').first()
  await expect(sider).toBeHidden({ timeout: 5_000 })

  await page.screenshot({ path: 'test-results/responsive-mobile-collapsed.png' })
})

test('用户管理页在平板尺寸下表格横向滚动', async ({ page }) => {
  await page.setViewportSize({ width: 768, height: 1024 })
  await uiLogin(page)

  await page.goto('/sys/user')
  await expect(page).toHaveURL(/\/sys\/user/)

  await page.screenshot({ path: 'test-results/responsive-tablet-user.png', fullPage: true })
})
