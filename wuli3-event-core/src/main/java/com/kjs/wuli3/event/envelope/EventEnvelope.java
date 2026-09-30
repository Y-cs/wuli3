package com.kjs.wuli3.event.envelope;

import com.kjs.wuli3.core.assertion.Asserts;
import java.time.Instant;

/**
 * 不可变且与具体传输实现无关的事件元数据和载荷。
 *
 * @param <T>        载荷类型
 * @param topic      逻辑远程目标主题
 * @param eventType  稳定的事件契约名称
 * @param eventId    唯一事件标识
 * @param occurredOn 事件创建时间
 * @param payload    事件载荷
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
public record EventEnvelope<T>(String topic, String eventType, String eventId, Instant occurredOn, T payload) {

    /**
     * 创建事件信封。
     */
    public EventEnvelope {
        topic = EventEnvelope.requireNonBlank(topic, "topic");
        eventType = EventEnvelope.requireNonBlank(eventType, "eventType");
        eventId = EventEnvelope.requireNonBlank(eventId, "eventId");
        Asserts.whenNull(occurredOn).throwIllegalArgumentException("occurredOn cannot be null");
        Asserts.whenNull(payload).throwIllegalArgumentException("payload cannot be null");
    }

    private static String requireNonBlank(final String value, final String name) {
        Asserts.whenNull(value).throwIllegalArgumentException(name + " cannot be null");
        Asserts.whenBlank(value).throwIllegalArgumentException(name + " cannot be blank");
        return value;
    }
}
