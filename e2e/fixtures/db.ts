import { Client } from 'pg'

/**
 * PostgreSQL 查询助手 —— 用于 E2E 测试中的 DB 状态断言。
 *
 * 通过环境变量覆盖连接参数（默认指向 docker-compose.local.yml 暴露的端口）：
 *   E2E_DB_HOST / E2E_DB_PORT / E2E_DB_NAME / E2E_DB_USER / E2E_DB_PASS
 *
 * 单例 Client：global-setup 中预热、global-teardown 中关闭；
 * 测试用例直接调用 queryOne / queryAll / execute 即可。
 */
let client: Client | null = null

export function getDbConfig() {
  return {
    host: process.env.E2E_DB_HOST || 'localhost',
    port: parseInt(process.env.E2E_DB_PORT || '5533'),
    database: process.env.E2E_DB_NAME || 'platform',
    user: process.env.E2E_DB_USER || 'postgres',
    password: process.env.E2E_DB_PASS || 'Postgres@2025',
  }
}

export async function getDb(): Promise<Client> {
  if (!client) {
    client = new Client(getDbConfig())
    await client.connect()
  }
  return client
}

export async function closeDb(): Promise<void> {
  if (client) {
    await client.end()
    client = null
  }
}

/** 查询单行；未命中返回 null */
export async function queryOne<T = any>(sql: string, params: any[] = []): Promise<T | null> {
  const db = await getDb()
  const res = await db.query(sql, params)
  return res.rows[0] || null
}

/** 查询多行 */
export async function queryAll<T = any>(sql: string, params: any[] = []): Promise<T[]> {
  const db = await getDb()
  const res = await db.query(sql, params)
  return res.rows
}

/** 执行 DML（INSERT/UPDATE/DELETE），返回受影响行数 */
export async function execute(sql: string, params: any[] = []): Promise<number> {
  const db = await getDb()
  const res = await db.query(sql, params)
  return res.rowCount || 0
}
