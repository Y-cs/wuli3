package com.kjs.wuli3.redis.codec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.kjs.wuli3.json.core.Jsons;
import com.kjs.wuli3.redis.RedisKeyPrefix;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 使用项目标准 {@link Jsons} 处理 Redis 结构化值的默认 Codec。
 *
 * 注意：key 和 Hash field 都是 UTF-8 字符串；key 序列化器在字符串序列化器之上叠加服务前缀。
 *
 * @author GuoYang create on 2026/9/7 10:40
 */
public final class JsonRedisCodec implements RedisCodec {

    /** 无状态 JSON Codec 单例。 */
    public static final JsonRedisCodec INSTANCE = new JsonRedisCodec();

    private static final RedisSerializer<String> KEY_SERIALIZER = StringRedisSerializer.UTF_8;

    private JsonRedisCodec() {}

    @Override
    public String encode(final Object value) {
        return Jsons.toJson(value);
    }

    @Override
    public <T> @Nullable T decode(final String encodedValue, final Class<T> type) {
        return Jsons.fromJson(encodedValue, type);
    }

    @Override
    public <T> @Nullable T decode(final String encodedValue, final TypeReference<T> typeReference) {
        return Jsons.fromJson(encodedValue, typeReference);
    }

    @Override
    public RedisSerializer<String> keySerializer(final RedisKeyPrefix keyPrefix) {
        return new RedisKeyPrefix.KeySerializer(keyPrefix, JsonRedisCodec.KEY_SERIALIZER);
    }

    @Override
    public RedisSerializer<String> hashKeySerializer() {
        return JsonRedisCodec.KEY_SERIALIZER;
    }
}
