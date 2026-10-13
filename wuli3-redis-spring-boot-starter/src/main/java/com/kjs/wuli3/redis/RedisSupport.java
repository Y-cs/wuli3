package com.kjs.wuli3.redis;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.redis.codec.RedisCodec;
import com.kjs.wuli3.redis.operation.HashRedisOperations;
import com.kjs.wuli3.redis.operation.ObjectRedisOperations;
import com.kjs.wuli3.redis.operation.SetRedisOperations;
import com.kjs.wuli3.redis.operation.StringRedisOperations;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/** 聚合不同 Redis 数据结构操作，并承载整 key 的通用操作。
 *
 * 注意：模板由本类按连接工厂、{@link RedisCodec} 和 {@link RedisKeyPrefix} 创建；key、field 的字节策略来自 Codec，
 * 值统一按 UTF-8 字符串传输，服务前缀由 Codec 的 key 序列化器携带，因此不读取应用声明的 RedisTemplate 配置。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
public final class RedisSupport {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisCodec codec;
    private final StringRedisOperations stringOperations;
    private final ObjectRedisOperations objectOperations;
    private final HashRedisOperations hashOperations;
    private final SetRedisOperations setOperations;

    /**
     * 使用连接工厂、Codec 和服务级 key 前缀创建 Redis 操作入口。
     *
     * 注意：值的格式由 Codec 的 {@code encode} 和 {@code decode} 承担；key 前缀由 {@link RedisKeyPrefix.KeySerializer} 叠加。
     */
    public RedisSupport(
            final RedisConnectionFactory connectionFactory, final RedisCodec codec, final RedisKeyPrefix keyPrefix) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.redisTemplate = RedisSupport.createRedisTemplate(connectionFactory, this.codec, keyPrefix);
        this.stringOperations = new StringRedisOperations(this.redisTemplate);
        this.objectOperations = new ObjectRedisOperations(this.redisTemplate, this.codec);
        this.hashOperations = new HashRedisOperations(this.redisTemplate, this.codec);
        this.setOperations = new SetRedisOperations(this.redisTemplate, this.codec);
    }

    /**
     * 按 Codec 的 key、field 序列化器装配模板。
     *
     * 注意：值统一按 UTF-8 字符串传输，格式由 {@link RedisCodec#encode} 和 {@link RedisCodec#decode} 承担；
     * key 前缀由 Codec 的 key 序列化器携带，本类不再叠加。
     */
    private static RedisTemplate<String, String> createRedisTemplate(
            final RedisConnectionFactory connectionFactory, final RedisCodec codec, final RedisKeyPrefix keyPrefix) {
        final RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(Objects.requireNonNull(connectionFactory, "connectionFactory"));
        template.setKeySerializer(codec.keySerializer(Objects.requireNonNull(keyPrefix, "keyPrefix")));
        template.setHashKeySerializer(codec.hashKeySerializer());
        template.setValueSerializer(StringRedisSerializer.UTF_8);
        template.setHashValueSerializer(StringRedisSerializer.UTF_8);
        template.afterPropertiesSet();
        return template;
    }

    /** 返回本入口使用的模板，key 已带服务前缀。 */
    public RedisTemplate<String, String> redisTemplate() {
        return this.redisTemplate;
    }

    /** 返回结构化值操作共享的 Codec。 */
    public RedisCodec codec() {
        return this.codec;
    }

    /** 返回字符串操作入口。 */
    public StringRedisOperations stringOperations() {
        return this.stringOperations;
    }

    /** 返回结构化对象操作入口。 */
    public ObjectRedisOperations objectOperations() {
        return this.objectOperations;
    }

    /** 返回 Hash 操作入口。 */
    public HashRedisOperations hashOperations() {
        return this.hashOperations;
    }

    /** 返回 Set 操作入口。 */
    public SetRedisOperations setOperations() {
        return this.setOperations;
    }

    /** 删除完整 key，并返回是否实际删除。 */
    public boolean delete(final RedisKey key) {
        Asserts.whenNull(key).throwIllegalArgumentException("Redis key must not be null");
        return this.redisTemplate.delete(key.value());
    }

    /** 批量删除完整 key，并返回实际删除数量。 */
    public long delete(final Collection<RedisKey> keys) {
        Asserts.whenNull(keys).throwIllegalArgumentException("Redis keys must not be null");
        final List<String> keyValues = keys.stream().map(RedisSupport::keyValue).toList();
        return this.redisTemplate.delete(keyValues);
    }

    private static String keyValue(final RedisKey key) {
        Asserts.whenNull(key).throwIllegalArgumentException("Redis key must not be null");
        return key.value();
    }

    /** 判断完整 key 是否存在。 */
    public boolean exists(final RedisKey key) {
        Asserts.whenNull(key).throwIllegalArgumentException("Redis key must not be null");
        return this.redisTemplate.hasKey(key.value());
    }

    /** 使用 key 自带的 TTL 重新设置过期时间。 */
    public boolean expire(final RedisKey key) {
        Asserts.whenNull(key).throwIllegalArgumentException("Redis key must not be null");
        final Duration timeToLive =
                key.timeToLive().orElseThrow(() -> new IllegalArgumentException("永久 Redis key 没有可刷新的过期时间"));
        return Boolean.TRUE.equals(this.redisTemplate.expire(key.value(), timeToLive));
    }

    /** 为指定 key 设置新的过期时间。 */
    public boolean expire(final String key, final Duration timeToLive) {
        return this.expire(RedisKey.expiring(key, timeToLive));
    }
}
