package com.example.common.cache;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 基础设施自动配置。
 *
 * <p>本配置类通过 Spring Boot 的 {@code AutoConfiguration.imports} SPI 自动注册，
 * 任何依赖 {@code platform-common} 的模块都会自动获得：
 *
 * <ul>
 *   <li>{@link RedisTemplate}&lt;String, Object&gt; —— value 使用
 *       {@link GenericJackson2JsonRedisSerializer}（携带类型信息，支持多态反序列化），
 *       key/hashKey 使用 {@link StringRedisSerializer}。
 *   <li>{@link StringRedisTemplate} —— 标准 String 模板。
 * </ul>
 *
 * <p>设计要点：
 *
 * <ul>
 *   <li><b>不</b>覆盖 {@link RedisConnectionFactory} —— 交由 Spring Boot
 *       {@code RedisAutoConfiguration} 按 {@code spring.data.redis.*} 属性自动装配
 *       Lettuce 连接工厂（Lettuce 连接懒初始化，上下文启动无需真实 Redis）。
 *   <li>{@link ConditionalOnClass} 守卫：仅当 classpath 存在 Redis 依赖时才激活，
 *       避免 Redis 未引入时上下文加载失败。
 *   <li>{@link EnableCaching} 启用 Spring Cache 抽象（供 {@code @Cacheable} 等使用），
 *       实际缓存管理器仍由 Spring Boot 默认提供。
 * </ul>
 *
 * <p>密码注入见 {@code application.yml}：{@code spring.data.redis.password:
 * ${REDIS_PASSWORD:${SPRING_DATA_REDIS_PASSWORD:}}}（REDIS_PASSWORD 优先，
 * 兼容历史变量 SPRING_DATA_REDIS_PASSWORD）。
 */
@AutoConfiguration
@ConditionalOnClass(RedisOperations.class)
@EnableCaching
public class RedisConfig {

    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = StringRedisSerializer.UTF_8;
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        RedisSerializer<Object> jsonSerializer = jsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @ConditionalOnMissingBean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    private RedisSerializer<Object> jsonRedisSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}
