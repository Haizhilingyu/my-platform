import type { Page } from '@playwright/test'
import { apiLogin, setAuth } from './auth'

/**
 * 测试 fixture：通过 API 登录拿 token，写入 localStorage，再导航到 path。
 * 用于所有 CRUD 测试，避免每个用例都走 UI 登录的慢路径。
 */
export async function loggedInPage(page: Page, path: string): Promise<void> {
  const token = await apiLogin()
  await setAuth(page, token)
  await page.goto(path)
  await page.waitForLoadState('networkidle')
}

/** 在当前 page context 拿到 localStorage 的 token（已登录场景） */
export async function getTokenFromPage(page: Page): Promise<string | null> {
  return page.evaluate(() => localStorage.getItem('token'))
}
