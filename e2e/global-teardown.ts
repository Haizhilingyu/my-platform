import { closeDb } from './fixtures/db'
import { closeRedis } from './fixtures/redis'

/** 全局 teardown：所有测试结束后关闭 DB / Redis 连接，释放事件循环 */
export default async function globalTeardown() {
  await closeDb().catch(() => {})
  await closeRedis().catch(() => {})
  console.log('[global-teardown] DB / Redis connections closed')
}
