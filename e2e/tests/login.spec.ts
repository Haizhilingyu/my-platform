import { test, expect } from '@playwright/test'

/**
 * Layer 2: 登录与核心导航 UI 端到端测试。
 *
 * 走前端 5173 → vite proxy → 后端 8090，验证真实前后端联调。
 * 默认账号 admin / admin123（Login.vue 表单预填），登录成功后 header
 * 显示 realName「超级管理员」。
 */

// 登录页固定文案（Login.vue）
const USERNAME_PH = '请输入用户名'
const PASSWORD_PH = '请输入密码'
const LOGIN_BTN = '登录'
// 登录成功后真实用户名（admin 的 realName）
const DISPLAY_NAME = '超级管理员'

// 每个用例自带登录/状态，相互独立，避免单点失败连累其他用例。

test('登录页正确渲染且字段已预填', async ({ page }) => {
  await page.goto('/login')

  // 关键控件可见
  await expect(page.getByPlaceholder(USERNAME_PH)).toBeVisible()
  await expect(page.getByPlaceholder(PASSWORD_PH)).toBeVisible()
  await expect(page.getByRole('button', { name: LOGIN_BTN })).toBeVisible()

  // 表单已预填默认凭据（Login.vue line 12）
  await expect(page.getByPlaceholder(USERNAME_PH)).toHaveValue('admin')
  await expect(page.getByPlaceholder(PASSWORD_PH)).toHaveValue('admin123')
})

test('路由守卫：未登录访问受保护页应重定向到 /login', async ({ page }) => {
  await page.goto('/')
  await expect(page).toHaveURL(/\/login/)
})

test('错误密码登录应被拒绝并提示', async ({ page }) => {
  await page.goto('/login')
  // 预填已为 admin/admin123。Naive UI NInput 受控，用全选+逐字符输入
  // 模拟真实打字，可靠触发 v-model 更新（fill 覆盖受控值会被 Vue 回滚）。
  const pwd = page.getByPlaceholder(PASSWORD_PH)
  await pwd.click()
  await pwd.press('ControlOrMeta+a')
  await pwd.press('Backspace')
  await pwd.pressSequentially('wrong-password', { delay: 20 })

  await page.getByRole('button', { name: LOGIN_BTN }).click()

  // 修复 http.ts 后，登录失败的 401 不再触发整页硬刷新，
  // Login.vue 的 message.error 能正常显示后端返回的错误信息。
  await expect(page.getByText('用户名或密码错误')).toBeVisible({ timeout: 10000 })
  // 仍停留在登录页，且未写入 token
  await expect(page).toHaveURL(/\/login/)
  const token = await page.evaluate(() => localStorage.getItem('token'))
  expect(token).toBeNull()
})

test('正确登录后进入主页', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: LOGIN_BTN }).click()

  // 成功 toast
  await expect(page.getByText('登录成功')).toBeVisible({ timeout: 10000 })
  // 跳转到 dashboard
  await expect(page).toHaveURL(/\/dashboard/)
  // header 显示真实用户名（realName）
  await expect(page.getByText(DISPLAY_NAME).first()).toBeVisible()
  // 侧栏品牌可见
  await expect(page.getByText('My Platform').first()).toBeVisible()
  // localStorage 已写入 token
  const token = await page.evaluate(() => localStorage.getItem('token'))
  expect(token).toBeTruthy()
})

test('登录后可导航到子页（带 token 接口在浏览器内联调通）', async ({ page }) => {
  // 先登录
  await page.goto('/login')
  await page.getByRole('button', { name: LOGIN_BTN }).click()
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10000 })

  // 侧栏 NMenu 是树形：「系统管理」是目录，需先点击展开，才会显示「用户管理」子项
  await page.getByText('系统管理').first().click()
  await page.getByText('用户管理').first().click()
  await expect(page).toHaveURL(/\/sys\/user/)
  // header 标题应切换为「用户管理」
  await expect(page.getByText('用户管理').first()).toBeVisible()
})

test('退出登录后回到登录页并清空 token', async ({ page }) => {
  // 先登录
  await page.goto('/login')
  await page.getByRole('button', { name: LOGIN_BTN }).click()
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10000 })

  // 点 header 用户区域展开下拉（点击头像/用户名容器）
  await page.getByText(DISPLAY_NAME).first().click()
  await page.getByText('退出登录').click()

  // 回到登录页
  await expect(page).toHaveURL(/\/login/)
  // token 已清空
  const token = await page.evaluate(() => localStorage.getItem('token'))
  expect(token).toBeNull()
})
