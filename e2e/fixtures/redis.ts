import Redis from 'ioredis'

/**
 * Redis 客户端单例 —— 用于 E2E 测试中的验证码解题与会话键断言。
 *
 * 环境变量：
 *   E2E_REDIS_HOST / E2E_REDIS_PORT / E2E_REDIS_PASS
 * 默认指向 docker-compose.local.yml 暴露的 localhost:6381。
 */
let redis: Redis | null = null

export function getRedis(): Redis {
  if (!redis) {
    redis = new Redis({
      host: process.env.E2E_REDIS_HOST || 'localhost',
      port: parseInt(process.env.E2E_REDIS_PORT || '6381'),
      password: process.env.E2E_REDIS_PASS || undefined,
      maxRetriesPerRequest: 3,
    })
  }
  return redis
}

export async function closeRedis(): Promise<void> {
  if (redis) {
    await redis.quit()
    redis = null
  }
}

/**
 * 根据 captchaId 从 Redis 取出验证码答案。
 *
 * 后端 RedisConfig 使用 GenericJackson2JsonRedisSerializer：String 是 final 类型，
 * NON_FINAL default typing 不会附加 @class，但 Jackson 仍会以 JSON 字符串形式序列化
 * （即两端带双引号 "ABCD"）。这里剔除可能的双引号，兼容裸字符串与 JSON 字符串两种情况。
 */
export async function getCaptchaAnswer(captchaId: string): Promise<string> {
  const r = getRedis()
  const value = await r.get(`captcha:${captchaId}`)
  if (!value) throw new Error(`Captcha answer not found for id: ${captchaId}`)
  return value.replace(/^"|"$/g, '')
}

/** 判断指定 key 是否存在于 Redis */
export async function redisExists(key: string): Promise<boolean> {
  const r = getRedis()
  const n = await r.exists(key)
  return n > 0
}

/** 删除指定 key，返回是否实际删除 */
export async function redisDelete(key: string): Promise<boolean> {
  const r = getRedis()
  const n = await r.del(key)
  return n > 0
}
