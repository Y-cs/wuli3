package com.kjs.wuli3.redis;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * 服务级 Redis key 前缀，用于隔离共用同一 Redis 实例的多个服务。
 *
 * 注意：前缀只作用于 Redis key，不作用于 Hash field 与 Set member；前缀只影响写入，读取按库中已有的完整 key 匹配，
 * 因此修改前缀后旧 key 不会自动迁移，也不会被新前缀读取。
 *
 * @author lipt create on 2026/10/12 18:11
 */
public record RedisKeyPrefix(String value) {

    /** 不添加前缀，key 按原样写入。 */
    public static final RedisKeyPrefix NONE = new RedisKeyPrefix("");

    private static final char SEPARATOR = ':';

    public RedisKeyPrefix {
        String normalized = Objects.requireNonNull(value, "value").trim();
        while (!normalized.isEmpty() && normalized.charAt(normalized.length() - 1) == RedisKeyPrefix.SEPARATOR) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        value = normalized;
    }

    /** 由配置值创建前缀；null 或空白表示不添加前缀，末尾分隔符会被去掉。 */
    public static RedisKeyPrefix of(final @Nullable String prefix) {
        return new RedisKeyPrefix(prefix == null ? "" : prefix);
    }

    /** 判断是否配置了前缀。 */
    public boolean isPresent() {
        return !this.value.isEmpty();
    }

    /** 返回实际写入 Redis 的完整 key；未配置前缀时返回原 key。 */
    public String wrap(final String key) {
        final String nonNullKey = Objects.requireNonNull(key, "key");
        return this.isPresent() ? this.value + RedisKeyPrefix.SEPARATOR + nonNullKey : nonNullKey;
    }

    /** 装饰 key 序列化器：写入时加服务前缀，读取时原样委托，因此不改变底层编码方式。 */
    public record KeySerializer(RedisKeyPrefix keyPrefix, RedisSerializer<String> delegate)
            implements RedisSerializer<String> {

        public KeySerializer {
            Objects.requireNonNull(keyPrefix, "keyPrefix");
            Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public byte @Nullable [] serialize(final @Nullable String value) {
            return this.delegate.serialize(value == null ? null : this.keyPrefix.wrap(value));
        }

        @Override
        public @Nullable String deserialize(final byte @Nullable [] bytes) {
            return this.delegate.deserialize(bytes);
        }
    }
}
