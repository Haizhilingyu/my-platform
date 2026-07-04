package com.example.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Redis 缓存服务（Testcontainers 集成）")
class RedisCacheServiceIT {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private RedisCacheService newService() {
        RedisStandaloneConfiguration cfg =
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379));
        LettuceConnectionFactory factory = new LettuceConnectionFactory(cfg);
        factory.afterPropertiesSet();
        RedisTemplate<String, Object> template = new RedisConfig().redisTemplate(factory);
        return new RedisCacheService(template);
    }

    @Test
    @DisplayName("set/get：写入对象可按类型读回（验证 Jackson 序列化往返）")
    void setAndGetObject_roundTripsWithSerialization() {
        RedisCacheService service = newService();
        service.set("it:user:1", new SampleValue("alice", 30));

        Optional<SampleValue> got = service.get("it:user:1", SampleValue.class);
        assertThat(got).isPresent();
        assertThat(got.get().name()).isEqualTo("alice");
        assertThat(got.get().age()).isEqualTo(30);
    }

    @Test
    @DisplayName("incr：连续自增为 1,2,3（Redis 原子 INCR）")
    void incr_isAtomicAcrossCalls() {
        RedisCacheService service = newService();
        assertThat(service.incr("it:counter")).isEqualTo(1L);
        assertThat(service.incr("it:counter")).isEqualTo(2L);
        assertThat(service.incr("it:counter")).isEqualTo(3L);
    }

    @Test
    @DisplayName("set(key,value,ttl) + expire：TTL 过期后 key 消失")
    void setWithTtl_expiresAfterTimeout() throws Exception {
        RedisCacheService service = newService();
        service.set("it:ephemeral", "v", Duration.ofMillis(200));
        assertThat(service.exists("it:ephemeral")).isTrue();
        Thread.sleep(400);
        assertThat(service.exists("it:ephemeral")).isFalse();
    }

    @Test
    @DisplayName("delete + keys：删除后 keys 模式匹配不再命中")
    void deleteRemovesFromKeys() {
        RedisCacheService service = newService();
        service.set("it:del:1", "a");
        service.set("it:del:2", "b");
        assertThat(service.keys("it:del:*")).isNotEmpty();
        service.delete("it:del:1");
        service.delete("it:del:2");
        assertThat(service.keys("it:del:*")).isNullOrEmpty();
    }

    record SampleValue(String name, int age) {}
}
