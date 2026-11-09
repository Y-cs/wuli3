package com.kjs.wuli3.rocket.v4;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.event.envelope.EventEnvelope;
import com.kjs.wuli3.event.error.SendFailedException;
import com.kjs.wuli3.event.remote.RemoteEventTransport;
import com.kjs.wuli3.rocket.message.RocketMessageWrapper;
import com.kjs.wuli3.rocket.message.RocketMessageWrapperEncoder;
import com.kjs.wuli3.rocket.transaction.RocketLocalTransactionRegistry;
import java.time.Duration;
import java.util.Objects;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * 基于 {@link RocketMQTemplate} 的默认尽力而为远程事件传输实现。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
public final class RocketRemoteEventTransport implements RemoteEventTransport<RocketV4PublishOptions> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RocketRemoteEventTransport.class);

    private final RocketMQTemplate rocketMQTemplate;
    private final RocketMessageWrapperEncoder encoder;
    private final RocketLocalTransactionRegistry transactionRegistry;

    /**
     * 使用指定的 RocketMQ 模板、公共事件编码器和本地事务注册表创建传输实现。
     *
     * @param rocketMQTemplate    Apache SDK 桥接模板
     * @param encoder             公共事件线协议编码器
     * @param transactionRegistry 按事件类型索引的本地事务
     */
    public RocketRemoteEventTransport(
            final RocketMQTemplate rocketMQTemplate,
            final RocketMessageWrapperEncoder encoder,
            final RocketLocalTransactionRegistry transactionRegistry) {
        this.rocketMQTemplate = Objects.requireNonNull(rocketMQTemplate, "rocketMQTemplate");
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.transactionRegistry = Objects.requireNonNull(transactionRegistry, "transactionRegistry");
    }

    @Override
    public Class<RocketV4PublishOptions> supportedOptionsType() {
        return RocketV4PublishOptions.class;
    }

    @Override
    public void send(final RocketV4PublishOptions options, final EventEnvelope<?>... envelopes) {
        Asserts.whenNull(options).throwIllegalArgumentException("publish options must not be null");
        Asserts.whenNull(envelopes).throwIllegalArgumentException("event envelopes must not be null");
        Asserts.whenTrue(options.delay() != null && (options.async() || options.orderKey() != null))
                .throwException(
                        CommonErrors.UNSUPPORTED_OPERATION,
                        "RocketMQ v4 exact delay does not support async or ordered publication");
        Asserts.whenTrue(options.transactional()
                        && (options.async() || options.delay() != null || options.orderKey() != null))
                .throwException(
                        CommonErrors.UNSUPPORTED_OPERATION,
                        "RocketMQ transactional message does not support async, delay or ordered publication");
        for (final EventEnvelope<?> envelope : envelopes) {
            if (options.transactional() && this.transactionRegistry.find(envelope.eventType()) == null) {
                throw new SendFailedException("No local transaction registered for event type " + envelope.eventType());
            }
            final RocketMessageWrapper wireMessage = this.encoder.encode(envelope, options.delay(), options.orderKey());
            this.sendEncoded(wireMessage, envelope, options);
        }
    }

    private void sendEncoded(
            final RocketMessageWrapper wireMessage,
            final EventEnvelope<?> envelope,
            final RocketV4PublishOptions options) {
        final Message<byte[]> message = MessageBuilder.withPayload(wireMessage.body())
                .copyHeaders(wireMessage.headers())
                .setHeader(RocketMQHeaders.KEYS, wireMessage.key())
                .build();
        // rocketmq-spring 只从 destination 的 topic:tag 形式解析 tag，消息头中的 TAGS 会被丢弃
        final String destination = wireMessage.topic() + ":" + wireMessage.tag();
        final SendCallback callback = new SafeCallback(envelope, options.sendCallback());
        if (options.transactional()) {
            this.sendInTransaction(destination, message, envelope, callback);
            return;
        }
        final SendResult result;
        try {
            if (options.async()) {
                if (wireMessage.orderKey() != null) {
                    this.rocketMQTemplate.asyncSendOrderly(destination, message, wireMessage.orderKey(), callback);
                } else {
                    this.rocketMQTemplate.asyncSend(destination, message, callback);
                }
                return;
            }
            final Duration delay = wireMessage.delay();
            if (delay != null) {
                result = this.rocketMQTemplate.syncSendDelayTimeMills(destination, message, delay.toMillis());
            } else if (wireMessage.orderKey() != null) {
                result = this.rocketMQTemplate.syncSendOrderly(destination, message, wireMessage.orderKey());
            } else {
                result = this.rocketMQTemplate.syncSend(destination, message);
            }
        } catch (final RuntimeException exception) {
            if (!options.async()) {
                callback.onException(exception);
            }
            if (exception instanceof ErrorCodeException error) {
                throw error;
            }
            throw new SendFailedException("RocketMQ event send failed", exception);
        }
        callback.onSuccess(result);
    }

    private void sendInTransaction(
            final String destination,
            final Message<byte[]> message,
            final EventEnvelope<?> envelope,
            final SendCallback callback) {
        final TransactionSendResult result;
        try {
            result = this.rocketMQTemplate.sendMessageInTransaction(destination, message, null);
        } catch (final RuntimeException exception) {
            callback.onException(exception);
            if (exception instanceof ErrorCodeException error) {
                throw error;
            }
            throw new SendFailedException("RocketMQ transactional event send failed", exception);
        }
        if (result.getLocalTransactionState() == LocalTransactionState.ROLLBACK_MESSAGE) {
            final SendFailedException failure =
                    new SendFailedException("RocketMQ transactional event rolled back: eventId=" + envelope.eventId());
            callback.onException(failure);
            throw failure;
        }
        if (result.getLocalTransactionState() == LocalTransactionState.UNKNOW) {
            RocketRemoteEventTransport.LOGGER.warn(
                    "RocketMQ transactional event local state unknown, awaiting broker check: eventId={}",
                    envelope.eventId());
        }
        callback.onSuccess(result);
    }

    /** 隔离业务回调异常，避免将通知失败误判为消息发送失败。 */
    private static final class SafeCallback implements SendCallback {
        private final EventEnvelope<?> envelope;
        private final @Nullable SendCallback delegate;

        private SafeCallback(final EventEnvelope<?> envelope, final @Nullable SendCallback delegate) {
            this.envelope = envelope;
            this.delegate = delegate;
        }

        @Override
        public void onSuccess(final SendResult result) {
            if (this.delegate != null) {
                try {
                    this.delegate.onSuccess(result);
                } catch (final RuntimeException exception) {
                    RocketRemoteEventTransport.LOGGER.error(
                            "RocketMQ success callback failed: eventId={}", this.envelope.eventId(), exception);
                }
            }
        }

        @Override
        public void onException(final Throwable failure) {
            if (this.delegate == null) {
                RocketRemoteEventTransport.LOGGER.error(
                        "RocketMQ publication failed: eventId={}", this.envelope.eventId(), failure);
                return;
            }
            try {
                this.delegate.onException(failure);
            } catch (final RuntimeException exception) {
                RocketRemoteEventTransport.LOGGER.error(
                        "RocketMQ failure callback failed: eventId={}", this.envelope.eventId(), exception);
            }
        }
    }
}
