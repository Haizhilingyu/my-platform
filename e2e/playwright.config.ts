import { defineConfig, devices } from '@playwright/test'

/**
 * Layer 2: Playwright UI 端到端测试配置。
 *
 * 走 前端 5173 → vite proxy /api → 后端 8090，验证真实前后端联调。
 * 服务栈（后端 + 前端 dev server）由 run-e2e.sh 统一编排启动，
 * 这里不再用 webServer 自动启动，避免重复或端口冲突。
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: false, // 单浏览器串行，避免共享 localStorage / 后端并发污染
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  outputDir: './test-results',

  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:5173',
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
