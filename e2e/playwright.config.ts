import { defineConfig, devices } from '@playwright/test'

/**
 * E2E 测试配置 —— 走 docker-compose 合并镜像（SPA + API + WebSocket）@ localhost:8090。
 *
 * DB / Redis 连接由 fixtures 直接持有（绕过应用），用于 DB 状态断言与验证码解题。
 * 单浏览器串行（workers:1），避免共享后端状态下的并发污染。
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  outputDir: './test-results',

  globalSetup: './global-setup.ts',
  globalTeardown: './global-teardown.ts',

  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:8090',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 15_000,
    navigationTimeout: 20_000,
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
