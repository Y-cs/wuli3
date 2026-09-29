package com.kjs.wuli3.redis.operation;

import com.kjs.wuli3.core.assertion.Asserts;
import com.fasterxml.jackson.core.type.TypeReference;
import com.kjs.wuli3.redis.RedisKey;
import com.kjs.wuli3.redis.codec.JsonRedisCodec;
import com.kjs.wuli3.redis.codec.RedisCodec;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 基于字符串 RedisTemplate 执行 Set JSON 操作。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
public final class SetRedisOperations {

    private final StringRedisTemplate redisTemplate;
    private final SetOperations<String, String> setOperations;
    private final RedisCodec codec;

    /** 使用标准 JSON Codec 创建 Set 操作入口。 */
    public SetRedisOperations(final StringRedisTemplate redisTemplate) {
        this(redisTemplate, JsonRedisCodec.INSTANCE);
    }

    /** 使用指定 Codec 创建 Set 操作入口。 */
    public SetRedisOperations(final StringRedisTemplate redisTemplate, final RedisCodec codec) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.setOperations = this.redisTemplate.opsForSet();
    }

    /** 添加成员，并在实际新增成员后刷新 key 的过期时间。 */
    public long add(final RedisKey key, final Object... values) {
        Asserts.whenNull(key).throwIllegalArgumentException("Redis key must not be null");
        final String[] encodedValues = this.encodeValues(values);
        final Long added = this.setOperations.add(key.value(), encodedValues);
        final long addedCount = added == null ? 0L : added;
        this.refreshAfterMutation(key, addedCount);
        return addedCount;
    }

    /** 删除成员并返回实际删除数量。 */
    public long remove(final RedisKey key, final Object... values) {
        Asserts.whenNull(key).throwIllegalArgumentException("Redis key must not be null");
        final String[] encodedValues = this.encodeValues(values);
        final Long removed = this.setOperations.remove(key.value(), (Object[]) encodedValues);
        return removed == null ? 0L : removed;
    }

    /** 判断成员是否存在。 */
    public boolean contains(final RedisKey key, final Object value) {
        Asserts.whenNull(key).throwIllegalArgumentException("Redis key must not be null");
        return Boolean.TRUE.equals(
                this.setOperations.isMember(key.value(), this.codec.encode(value)));
    }

    /** 按具体类型读取全部成员。 */
    public <T> Set<T> members(final RedisKey key, final Class<T> type) {
        Asserts.whenNull(type).throwIllegalArgumentException("Redis value type must not be null");
        return this.decodeMembers(key, encodedValue -> this.codec.decode(encodedValue, type));
    }

    /** 按泛型类型读取全部成员。 */
    public <T> Set<T> members(final RedisKey key, final TypeReference<T> typeReference) {
        Asserts.whenNull(typeReference).throwIllegalArgumentException("Redis value type reference must not be null");
        return this.decodeMembers(key, encodedValue -> this.codec.decode(encodedValue, typeReference));
    }

    /** 返回成员数量。 */
    public long size(final RedisKey key) {
        Asserts.whenNull(key).throwIllegalArgumentException("Redis key must not be null");
        final Long size = this.setOperations.size(key.value());
        return size == null ? 0L : size;
    }

    private void refreshExpiration(final RedisKey key) {
        Asserts.whenNull(key).throwIllegalArgumentException("Redis key must not be null");
        final Duration timeToLive =
                key.timeToLive().orElseThrow(() -> new IllegalArgumentException("永久 Redis key 没有可刷新的过期时间"));
        this.redisTemplate.expire(key.value(), timeToLive);
    }

    private <T> Set<T> decodeMembers(final RedisKey key, final Function<String, @Nullable T> decoder) {
        Asserts.whenNull(key).throwIllegalArgumentException("Redis key must not be null");
        final Set<String> encodedMembers = this.setOperations.members(key.value());
        if (encodedMembers == null || encodedMembers.isEmpty()) {
            return Set.of();
        }
        final Set<T> decodedMembers = new LinkedHashSet<>();
        encodedMembers.forEach(json -> {
            final @Nullable T decoded = decoder.apply(json);
            if (decoded != null) {
                decodedMembers.add(decoded);
            }
        });
        return Set.copyOf(decodedMembers);
    }

    private void refreshAfterMutation(final RedisKey key, final long mutationCount) {
        if (mutationCount > 0L && key.timeToLive().isPresent()) {
            this.refreshExpiration(key);
        }
    }

    private String[] encodeValues(final Object[] values) {
        Asserts.whenNull(values).throwIllegalArgumentException("Redis values must not be null");
        if (values.length == 0) {
            throw new IllegalArgumentException("Redis Set 操作至少需要一个成员");
        }
        final String[] encodedValues = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            Asserts.whenNull(values[index]).throwIllegalArgumentException("Redis value must not be null");
            encodedValues[index] = this.codec.encode(values[index]);
        }
        return encodedValues;
    }
}
