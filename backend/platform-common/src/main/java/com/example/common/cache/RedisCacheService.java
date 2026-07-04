package com.example.common.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis 操作封装服务。
 *
 * <p>提供 set/get/delete/exists/incr/expire/keys 等基础能力。{@code incr} 通过
 * {@link org.springframework.data.redis.core.ValueOperations#increment} 实现，
 * 为 Redis 原子操作，可用于登录失败计数（T8）、限流、分布式计数等场景。
 *
 * <p>反序列化依赖 {@link RedisConfig} 中 {@code GenericJackson2JsonRedisSerializer}
 * 携带的类型信息，{@link #get(String, Class)} 直接按目标类型转换。
 */
@Service
public class RedisCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    /**
     * 读取并按目标类型转换。值不存在或类型不匹配时返回 {@link Optional#empty()}。
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (!type.isInstance(value)) {
            return Optional.empty();
        }
        return Optional.of((T) value);
    }

    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    public boolean exists(String key) {
        Boolean hasKey = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(hasKey);
    }

    /**
     * 原子自增 1（Redis INCR）。key 不存在时初始化为 1。
     */
    public long incr(String key) {
        Long result = redisTemplate.opsForValue().increment(key);
        return result == null ? 0L : result;
    }

    /**
     * 原子自增 {@code delta}（Redis INCRBY）。
     */
    public long incr(String key, long delta) {
        Long result = redisTemplate.opsForValue().increment(key, delta);
        return result == null ? 0L : result;
    }

    public Boolean expire(String key, Duration ttl) {
        return redisTemplate.expire(key, ttl);
    }

    /**
     * 按模式匹配 key（Redis KEYS）。生产环境慎用，大数据集下会阻塞。
     */
    public Set<String> keys(String pattern) {
        return redisTemplate.keys(pattern);
    }
}
