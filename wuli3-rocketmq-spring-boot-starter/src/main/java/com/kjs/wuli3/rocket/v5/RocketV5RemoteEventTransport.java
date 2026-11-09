package com.kjs.wuli3.rocket.v5;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.event.envelope.EventEnvelope;
import com.kjs.wuli3.event.error.SendFailedException;
import com.kjs.wuli3.event.remote.RemoteEventTransport;
import com.kjs.wuli3.rocket.message.RocketMessageWrapper;
import com.kjs.wuli3.rocket.message.RocketMessageWrapperEncoder;
import com.kjs.wuli3.rocket.transaction.RocketLocalTransaction;
import com.kjs.wuli3.rocket.transaction.RocketLocalTransactionRegistry;
import com.kjs.wuli3.rocket.transaction.RocketLocalTransactionState;
import com.kjs.wuli3.rocket.transaction.RocketTransactionEvent;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.message.MessageBuilder;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.apis.producer.Transaction;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 RocketMQ Java Client v5 的远程事件传输实现。
 *
 * <p>应用负责创建并关闭 {@link Producer}；starter 只在选择 v5 客户端时注入该 Producer。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
@RequiredArgsConstructor
public final class RocketV5RemoteEventTransport implements RemoteEventTransport<RocketV5PublishOptions> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RocketV5RemoteEventTransport.class);

    @NonNull
    private final Producer producer;

    @NonNull
    private final ClientServiceProvider clientServiceProvider;

    @NonNull
    private final RocketMessageWrapperEncoder encoder;

    @NonNull
    private final Clock clock;

    @NonNull
    private final RocketLocalTransactionRegistry transactionRegistry;

    @Override
    public Class<RocketV5PublishOptions> supportedOptionsType() {
        return RocketV5PublishOptions.class;
    }

    @Override
    public void send(final RocketV5PublishOptions options, final EventEnvelope<?>... envelopes) {
        Asserts.whenNull(options).throwIllegalArgumentException("publish options must not be null");
        Asserts.whenNull(envelopes).throwIllegalArgumentException("event envelopes must not be null");
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
            final Message message = this.createMessage(wireMessage, options);
            if (options.transactional()) {
                this.sendInTransaction(message, wireMessage, options);
                continue;
            }
            if (options.async()) {
                this.sendAsync(message, envelope, options);
                continue;
            }
            final SendReceipt receipt;
            try {
                receipt = this.producer.send(message);
            } catch (final ClientException | RuntimeException exception) {
                this.notifyCallback(options, null, exception);
                throw new SendFailedException("RocketMQ event send failed", exception);
            }
            this.notifyCallback(options, receipt, null);
        }
    }

    private Message createMessage(final RocketMessageWrapper wireMessage, final RocketV5PublishOptions options) {
        final String orderKey = wireMessage.orderKey();
        final Duration delay = wireMessage.delay();
        if (orderKey != null && delay != null) {
            throw new ErrorCodeException(
                    CommonErrors.UNSUPPORTED_OPERATION,
                    "RocketMQ Java Client does not support ordered delayed " + "messages");
        }
        if (orderKey != null && options.async()) {
            throw new ErrorCodeException(
                    CommonErrors.UNSUPPORTED_OPERATION, "RocketMQ Java Client does not support async FIFO messages");
        }

        final MessageBuilder builder = this.clientServiceProvider
                .newMessageBuilder()
                .setTopic(wireMessage.topic())
                .setBody(wireMessage.body())
                .setKeys(wireMessage.key())
                .setTag(wireMessage.tag());
        wireMessage.headers().forEach((key, value) -> builder.addProperty(key, String.valueOf(value)));
        if (orderKey != null) {
            builder.setMessageGroup(orderKey);
        }
        if (delay != null) {
            builder.setDeliveryTimestamp(Math.addExact(this.clock.millis(), delay.toMillis()));
        }
        return builder.build();
    }

    private void sendInTransaction(
            final Message message, final RocketMessageWrapper wireMessage, final RocketV5PublishOptions options) {
        final RocketTransactionEvent event;
        final RocketLocalTransaction localTransaction;
        try {
            event = RocketTransactionEvent.fromJson(new String(wireMessage.body(), StandardCharsets.UTF_8));
            localTransaction = Objects.requireNonNull(this.transactionRegistry.find(event.eventType()));
        } catch (final RuntimeException exception) {
            throw new SendFailedException("RocketMQ transactional event could not be resolved", exception);
        }
        final Transaction transaction;
        try {
            transaction = this.producer.beginTransaction();
        } catch (final ClientException | RuntimeException exception) {
            throw new SendFailedException("RocketMQ transactional event send failed to start", exception);
        }
        final SendReceipt receipt;
        try {
            receipt = this.producer.send(message, transaction);
        } catch (final ClientException | RuntimeException exception) {
            RocketV5RemoteEventTransport.rollbackQuietly(transaction);
            this.notifyCallback(options, null, exception);
            throw new SendFailedException("RocketMQ transactional event send failed", exception);
        }
        final RocketLocalTransactionState state;
        try {
            state = localTransaction.execute(event);
        } catch (final RuntimeException exception) {
            RocketV5RemoteEventTransport.LOGGER.error(
                    "RocketMQ transactional local execution failed, awaiting broker check: eventId={}",
                    event.eventId(),
                    exception);
            this.notifyCallback(options, null, exception);
            return;
        }
        try {
            switch (state) {
                case COMMIT -> transaction.commit();
                case ROLLBACK -> transaction.rollback();
                case UNKNOWN ->
                    RocketV5RemoteEventTransport.LOGGER.warn(
                            "RocketMQ transactional event local state unknown, awaiting broker check: eventId={}",
                            event.eventId());
            }
        } catch (final ClientException | RuntimeException exception) {
            this.notifyCallback(options, null, exception);
            throw new SendFailedException("RocketMQ transactional event settlement failed", exception);
        }
        if (state == RocketLocalTransactionState.ROLLBACK) {
            final SendFailedException failure =
                    new SendFailedException("RocketMQ transactional event rolled back: eventId=" + event.eventId());
            this.notifyCallback(options, null, failure);
            throw failure;
        }
        this.notifyCallback(options, receipt, null);
    }

    private static void rollbackQuietly(final Transaction transaction) {
        try {
            transaction.rollback();
        } catch (final ClientException | RuntimeException exception) {
            RocketV5RemoteEventTransport.LOGGER.warn("RocketMQ transactional rollback failed", exception);
        }
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private void sendAsync(
            final Message message, final EventEnvelope<?> envelope, final RocketV5PublishOptions options) {
        try {
            this.producer.sendAsync(message).whenComplete((receipt, throwable) -> {
                if (options.sendCallback() != null) {
                    this.notifyCallback(options, receipt, throwable);
                } else if (throwable != null) {
                    RocketV5RemoteEventTransport.LOGGER.error(
                            "Async RocketMQ Java Client v5 event publication failed: topic={}, eventId={},"
                                    + " eventType={}",
                            envelope.topic(),
                            envelope.eventId(),
                            envelope.eventType(),
                            throwable);
                }
            });
        } catch (final RuntimeException exception) {
            throw new SendFailedException("RocketMQ async event send failed to start", exception);
        }
    }

    /** 隔离业务回调异常，避免改变发送结果或覆盖原发送异常。 */
    private void notifyCallback(
            final RocketV5PublishOptions options,
            final @Nullable SendReceipt receipt,
            final @Nullable Throwable throwable) {
        final var callback = options.sendCallback();
        if (callback != null) {
            try {
                callback.accept(throwable == null ? receipt : null, throwable);
            } catch (final RuntimeException exception) {
                RocketV5RemoteEventTransport.LOGGER.error("RocketMQ v5 send callback failed", exception);
            }
        }
    }
}
