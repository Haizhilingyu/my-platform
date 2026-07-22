import { getDb, execute } from './fixtures/db'
import { getRedis } from './fixtures/redis'

/**
 * 全局 setup：在所有测试开始前
 *   1. 预热 DB / Redis 连接（fail-fast：基础设施不可用直接退出）
 *   2. 清理上次运行残留的 E2E 测试数据（按命名约定 E2E_* / e2e_* 前缀；下划线符合用户名/角色/单位 code 的 pattern 约束）
 *
 * 不在这里做应用启动 —— 应用栈由 run-e2e.sh 通过 docker compose 拉起。
 */
export default async function globalSetup() {
  console.log('[global-setup] warming up DB / Redis connections...')
  const db = await getDb()
  const redis = getRedis()
  await redis.ping()

  // 清理历史 E2E 残留数据（按命名约定，不会动到播种的 admin/默认角色等）
  await execute("DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE 'e2e\\_%')")
  await execute("DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE role_code LIKE 'e2e\\_%')")
  await execute("DELETE FROM sys_user WHERE username LIKE 'e2e\\_%'")
  await execute("DELETE FROM sys_role WHERE role_code LIKE 'e2e\\_%'")
  await execute("DELETE FROM sys_unit WHERE unit_code LIKE 'E2E\\_%'")
  await execute("DELETE FROM sys_menu WHERE menu_name LIKE 'E2E%' OR permission LIKE 'e2e:%'")
  await execute("DELETE FROM sys_config WHERE config_key LIKE 'e2e.%'")

  console.log('[global-setup] DB / Redis ready, leftover e2e data cleared')
}
