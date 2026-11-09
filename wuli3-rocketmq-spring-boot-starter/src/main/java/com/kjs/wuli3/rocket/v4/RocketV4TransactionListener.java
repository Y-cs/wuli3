package com.kjs.wuli3.rocket.v4;

import com.kjs.wuli3.rocket.transaction.RocketLocalTransaction;
import com.kjs.wuli3.rocket.transaction.RocketLocalTransactionRegistry;
import com.kjs.wuli3.rocket.transaction.RocketLocalTransactionState;
import com.kjs.wuli3.rocket.transaction.RocketTransactionEvent;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;

/**
 * 将 RocketMQ 事务消息回调分发给按事件类型注册的本地事务。
 *
 * <p>注意：rocketmq-spring 每个 {@code RocketMQTemplate} 只允许一个事务监听器；业务自定义
 * {@link RocketMQLocalTransactionListener} Bean 时本实现自动让位。
 *
 * @author lipt create on 2026/11/9 10:57
 */
@RocketMQTransactionListener
@SuppressWarnings("rawtypes")
public final class RocketV4TransactionListener implements RocketMQLocalTransactionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RocketV4TransactionListener.class);

    private final RocketLocalTransactionRegistry registry;

    /**
     * 创建事务回调分发器。
     *
     * @param registry 按事件类型索引的本地事务
     */
    public RocketV4TransactionListener(final RocketLocalTransactionRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(final Message message, final @Nullable Object arg) {
        return this.dispatch(message, true);
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(final Message message) {
        return this.dispatch(message, false);
    }

    private RocketMQLocalTransactionState dispatch(final Message message, final boolean execute) {
        try {
            final byte[] body = (byte[]) message.getPayload();
            final RocketTransactionEvent event =
                    RocketTransactionEvent.fromJson(new String(body, StandardCharsets.UTF_8));
            final RocketLocalTransaction transaction = this.registry.find(event.eventType());
            if (transaction == null) {
                LOGGER.warn("No local transaction registered for event type {}", event.eventType());
                return RocketMQLocalTransactionState.UNKNOWN;
            }
            final RocketLocalTransactionState state = execute ? transaction.execute(event) : transaction.check(event);
            return RocketV4TransactionListener.toSdkState(state);
        } catch (final RuntimeException exception) {
            LOGGER.error("RocketMQ local transaction {} failed", execute ? "execution" : "check", exception);
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }

    private static RocketMQLocalTransactionState toSdkState(final RocketLocalTransactionState state) {
        return switch (state) {
            case COMMIT -> RocketMQLocalTransactionState.COMMIT;
            case ROLLBACK -> RocketMQLocalTransactionState.ROLLBACK;
            case UNKNOWN -> RocketMQLocalTransactionState.UNKNOWN;
        };
    }
}
