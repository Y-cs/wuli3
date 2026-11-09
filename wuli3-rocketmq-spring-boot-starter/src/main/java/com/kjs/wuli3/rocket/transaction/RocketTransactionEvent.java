package com.kjs.wuli3.rocket.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.json.core.Jsons;

/**
 * 本地事务执行与回查时可见的事务消息内容。
 *
 * <p>由 RocketMQ 线消息体还原；回查路径拿不到发送时的业务参数，因此只暴露线协议字段。
 *
 * @param topic       事件主题
 * @param eventType   事件类型，同时作为本地事务的匹配键
 * @param eventId     事件唯一标识
 * @param payloadJson 事件载荷 JSON
 *
 * @author lipt create on 2026/11/9 10:57
 */
public record RocketTransactionEvent(String topic, String eventType, String eventId, String payloadJson) {

    /** 校验线协议字段完整。 */
    public RocketTransactionEvent {
        Asserts.whenBlank(topic).throwIllegalArgumentException("topic cannot be blank");
        Asserts.whenBlank(eventType).throwIllegalArgumentException("eventType cannot be blank");
        Asserts.whenBlank(eventId).throwIllegalArgumentException("eventId cannot be blank");
        Asserts.whenNull(payloadJson).throwIllegalArgumentException("payloadJson cannot be null");
    }

    /**
     * 从编码后的事件信封 JSON 还原事务消息内容。
     *
     * @param envelopeJson RocketMQ 消息体 JSON
     * @return 事务消息内容
     * @throws ErrorCodeException 消息体不是合法的事件信封时抛出
     */
    public static RocketTransactionEvent fromJson(final String envelopeJson) {
        final JsonNode node = Jsons.fromJson(envelopeJson, JsonNode.class);
        if (node == null) {
            throw new ErrorCodeException(
                    CommonErrors.ILLEGAL_ARGUMENT, "transactional event envelope must not be null");
        }
        return new RocketTransactionEvent(
                node.path("topic").asText(),
                node.path("eventType").asText(),
                node.path("eventId").asText(),
                node.path("payload").toString());
    }
}
