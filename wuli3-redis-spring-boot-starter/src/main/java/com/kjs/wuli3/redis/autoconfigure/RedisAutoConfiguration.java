package com.kjs.wuli3.redis.autoconfigure;

import com.kjs.wuli3.redis.RedisKeyPrefix;
import com.kjs.wuli3.redis.RedisSupport;
import com.kjs.wuli3.redis.codec.JsonRedisCodec;
import com.kjs.wuli3.redis.codec.RedisCodec;
import com.kjs.wuli3.redis.lock.RedisLockExecutor;
import com.kjs.wuli3.redis.lock.RedissonRedisLockExecutor;
import com.kjs.wuli3.spring.autoconfigure.ApplicationServiceProperties;
import com.kjs.wuli3.spring.autoconfigure.CoreSpringBootAutoConfiguration;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 基于应用持有的 Redis 连接配置类型安全的结构化值操作和分布式锁，默认使用 JSON Codec。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
@AutoConfiguration(
        after = CoreSpringBootAutoConfiguration.class,
        afterName = {
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.redisson.spring.starter.RedissonAutoConfigurationV2"
        })
@EnableConfigurationProperties(RedisProperties.class)
public class RedisAutoConfiguration {

    /** 注册默认 JSON Codec；应用提供 {@link RedisCodec} Bean 时自动让出。 */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(RedisCodec.class)
    @ConditionalOnProperty(
            prefix = "wuli3.redis.operations",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    RedisCodec redisCodec() {
        return JsonRedisCodec.INSTANCE;
    }

    /** 聚合各数据结构操作，并集中提供整 key 操作；模板和序列化策略由 RedisSupport 按连接工厂和 Codec 创建。 */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(RedisSupport.class)
    @ConditionalOnProperty(
            prefix = "wuli3.redis.operations",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    RedisSupport redisSupport(
            final RedisConnectionFactory connectionFactory,
            final RedisCodec codec,
            final ApplicationServiceProperties serviceProperties) {
        if (serviceProperties.getServiceCode().isBlank()) {
            throw new IllegalStateException(
                    "RedisSupport 要求配置 application.service.service-code 或 spring.application.name 作为 Redis key 前缀");
        }
        return new RedisSupport(connectionFactory, codec, RedisKeyPrefix.of(serviceProperties.getServiceCode()));
    }

    /** 创建默认 Redisson 锁执行器，但不接管 RedissonClient 的生命周期。 */
    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean(RedisLockExecutor.class)
    @ConditionalOnProperty(prefix = "wuli3.redis.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
    RedisLockExecutor redisLockExecutor(final RedissonClient redissonClient) {
        return new RedissonRedisLockExecutor(redissonClient);
    }
}
