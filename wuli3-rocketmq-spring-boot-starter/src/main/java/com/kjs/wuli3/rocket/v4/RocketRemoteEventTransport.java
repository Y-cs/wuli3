package com.kjs.wuli3.rocket.v4;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.event.envelope.EventEnvelope;
import com.kjs.wuli3.event.error.SendFailedException;
import com.kjs.wuli3.event.remote.RemoteEventTransport;
import com.kjs.wuli3.rocket.message.RocketMessageWrapper;
import com.kjs.wuli3.rocket.message.RocketMessageWrapperEncoder;
import java.time.Duration;
import java.util.Objects;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
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

    /**
     * 使用指定的 RocketMQ 模板和公共事件编码器创建传输实现。
     *
     * @param rocketMQTemplate Apache SDK 桥接模板
     * @param encoder          公共事件线协议编码器
     */
    public RocketRemoteEventTransport(
            final RocketMQTemplate rocketMQTemplate, final RocketMessageWrapperEncoder encoder) {
        this.rocketMQTemplate = Objects.requireNonNull(rocketMQTemplate, "rocketMQTemplate");
        this.encoder = Objects.requireNonNull(encoder, "encoder");
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
        for (final EventEnvelope<?> envelope : envelopes) {
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
                .setHeader(RocketMQHeaders.TAGS, wireMessage.tag())
                .build();
        final SendCallback callback = new SafeCallback(envelope, options.sendCallback());
        final SendResult result;
        try {
            if (options.async()) {
                if (wireMessage.orderKey() != null) {
                    this.rocketMQTemplate.asyncSendOrderly(
                            wireMessage.topic(), message, wireMessage.orderKey(), callback);
                } else {
                    this.rocketMQTemplate.asyncSend(wireMessage.topic(), message, callback);
                }
                return;
            }
            final Duration delay = wireMessage.delay();
            if (delay != null) {
                result = this.rocketMQTemplate.syncSendDelayTimeMills(wireMessage.topic(), message, delay.toMillis());
            } else if (wireMessage.orderKey() != null) {
                result = this.rocketMQTemplate.syncSendOrderly(wireMessage.topic(), message, wireMessage.orderKey());
            } else {
                result = this.rocketMQTemplate.syncSend(wireMessage.topic(), message);
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
