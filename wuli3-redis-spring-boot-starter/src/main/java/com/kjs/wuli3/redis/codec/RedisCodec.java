package com.kjs.wuli3.redis.codec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.kjs.wuli3.redis.RedisKeyPrefix;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Redis 结构化值的编码约定，以及 key、Hash field 的序列化策略。
 *
 * <p>{@code encode} 和 {@code decode} 定义值与字符串之间的格式，读写都经过这两个方法，作用于
 * {@code ObjectRedisOperations}、Hash value 和 Set member。值统一按 UTF-8 字符串写入 Redis，因此本接口不提供值序列化器。
 *
 * <p>{@link #keySerializer} 和 {@link #hashKeySerializer} 定义 key、field 字符串与字节之间的转换，由
 * {@code RedisSupport} 装配到自己创建的模板上，使 key 和 field 的字节策略完全由本接口决定，不依赖应用声明的 RedisTemplate。
 *
 * 注意：{@link #keySerializer} 返回的序列化器必须携带传入的服务前缀；{@link #hashKeySerializer} 不携带前缀，
 * 因为 Hash field 位于已带前缀的 key 之内。
 *
 * @author GuoYang create on 2026/9/7 10:40
 */
public interface RedisCodec {

    /** 将结构化值编码为 Redis 字符串。 */
    String encode(Object value);

    /** 按具体类型解码 Redis 字符串；编码内容为顶层 {@code null} 时返回 {@code null}。 */
    <T> @Nullable T decode(String encodedValue, Class<T> type);

    /** 按泛型类型解码 Redis 字符串；编码内容为顶层 {@code null} 时返回 {@code null}。 */
    <T> @Nullable T decode(String encodedValue, TypeReference<T> typeReference);

    /**
     * 返回携带服务前缀的 key 序列化器，决定实际写入 Redis 的 key 字节。
     *
     * @param keyPrefix 由 {@code RedisSupport} 提供的服务前缀，实现必须叠加到 key 上
     */
    RedisSerializer<String> keySerializer(RedisKeyPrefix keyPrefix);

    /** 返回 Hash field 序列化器，决定 field 字符串与字节之间的转换，不携带服务前缀。 */
    RedisSerializer<String> hashKeySerializer();
}
