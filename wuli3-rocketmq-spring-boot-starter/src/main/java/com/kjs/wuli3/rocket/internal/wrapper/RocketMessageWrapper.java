package com.kjs.wuli3.rocket.internal.wrapper;

import com.kjs.wuli3.core.assertion.Asserts;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * 由公共编码器生成、与 SDK 无关的 RocketMQ 事件消息。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
public record RocketMessageWrapper(
        String topic,
        byte[] body,
        Map<String, Object> headers,
        String key,
        String tag,
        @Nullable String orderKey,
        @Nullable Duration delay) {

    /**
     * 创建带有防御性拷贝的 SDK 无关消息值。
     *
     * @param topic    目标主题
     * @param body     序列化后的事件信封
     * @param headers  RocketMQ 消息属性
     * @param key      事件标识
     * @param tag      事件类型标签，用于 broker 侧过滤
     * @param orderKey 可选顺序键
     * @param delay    可选的精确延迟
     */
    public RocketMessageWrapper(
            final String topic,
            final byte[] body,
            final Map<String, Object> headers,
            final String key,
            final String tag,
            final @Nullable String orderKey,
            final @Nullable Duration delay) {
        Asserts.whenNull(topic).throwIllegalArgumentException("topic must not be null");
        Asserts.whenNull(body).throwIllegalArgumentException("body must not be null");
        Asserts.whenNull(headers).throwIllegalArgumentException("headers must not be null");
        Asserts.whenNull(key).throwIllegalArgumentException("key must not be null");
        Asserts.whenNull(tag).throwIllegalArgumentException("tag must not be null");
        headers.forEach((name, value) -> {
            Asserts.whenNull(name).throwIllegalArgumentException("header name must not be null");
            Asserts.whenNull(value).throwIllegalArgumentException("header value must not be null");
        });
        this.topic = topic;
        this.body = Arrays.copyOf(body, body.length);
        this.headers = Map.copyOf(headers);
        this.key = key;
        this.tag = tag;
        this.orderKey = orderKey;
        this.delay = delay;
    }
}
