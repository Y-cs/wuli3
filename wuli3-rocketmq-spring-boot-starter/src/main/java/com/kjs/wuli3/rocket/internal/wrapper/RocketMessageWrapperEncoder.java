package com.kjs.wuli3.rocket.internal.wrapper;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.event.envelope.EventEnvelope;
import com.kjs.wuli3.json.core.Jsons;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.propagation.context.ContextSnapshot;
import com.kjs.wuli3.propagation.store.ContextReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * 在不依赖任一 RocketMQ Producer SDK 的前提下编码公共事件线协议。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
public final class RocketMessageWrapperEncoder {

    private static final int MAX_TOPIC_BYTES = 127;
    private static final Pattern TOPIC_PATTERN = Pattern.compile("[%a-zA-Z0-9_-]+");
    private final @Nullable ContextReader contextReader;
    private final ContextPropagator contextPropagator;

    /**
     * 使用当前上下文重建保留传播头信息的编码器。
     *
     * @param contextReader  可选的当前上下文读取器
     * @param contextPropagator 上下文字段编码器
     */
    public RocketMessageWrapperEncoder(
            final @Nullable ContextReader contextReader, final ContextPropagator contextPropagator) {
        this.contextReader = contextReader;
        this.contextPropagator = Objects.requireNonNull(contextPropagator, "contextEncoder");
    }

    /**
     * 校验并序列化事件，不调用任一 RocketMQ 客户端 SDK。
     *
     * @param envelope 待序列化事件
     * @param delay 精确延迟
     * @param orderKey 顺序键
     * @return 与 SDK 无关的线消息
     */
    public RocketMessageWrapper encode(
            final EventEnvelope<?> envelope, final @Nullable Duration delay, final @Nullable String orderKey) {
        Asserts.whenNull(envelope).throwIllegalArgumentException("event envelope must not be null");
        RocketMessageWrapperEncoder.validateTopic(envelope.topic(), envelope.eventId());

        final Map<String, Object> headers = this.propagationHeaders();
        return new RocketMessageWrapper(
                envelope.topic(),
                Jsons.toJsonBytes(envelope),
                headers,
                envelope.eventId(),
                envelope.eventType(),
                orderKey,
                delay);
    }

    /**
     * 处理传播头
     *
     * @return 当前调用需要传播的消息头
     */
    private Map<String, Object> propagationHeaders() {
        final Map<String, Object> headers = new LinkedHashMap<>();
        if (this.contextReader == null) {
            return headers;
        }
        // 获取当前上下文并添加传播头
        final ContextSnapshot snapshot = ContextSnapshot.from(this.contextReader.state());
        this.contextPropagator.inject(snapshot, headers::put);
        return headers;
    }

    private static void validateTopic(final String topic, final String eventId) {
        Asserts.whenNull(topic).throwIllegalArgumentException("topic must not be null");
        if (!RocketMessageWrapperEncoder.TOPIC_PATTERN.matcher(topic).matches()
                || topic.getBytes(StandardCharsets.UTF_8).length > RocketMessageWrapperEncoder.MAX_TOPIC_BYTES) {
            throw new ErrorCodeException(
                    CommonErrors.ILLEGAL_ARGUMENT,
                    "Invalid RocketMQ topic for event " + eventId
                            + "; use 1-127 bytes containing only letters, digits, %, -, or _");
        }
    }
}
