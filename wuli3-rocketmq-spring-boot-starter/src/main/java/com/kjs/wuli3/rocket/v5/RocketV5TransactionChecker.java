package com.kjs.wuli3.rocket.v5;

import com.kjs.wuli3.rocket.transaction.RocketLocalTransaction;
import com.kjs.wuli3.rocket.transaction.RocketLocalTransactionRegistry;
import com.kjs.wuli3.rocket.transaction.RocketLocalTransactionState;
import com.kjs.wuli3.rocket.transaction.RocketTransactionEvent;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.apis.producer.TransactionChecker;
import org.apache.rocketmq.client.apis.producer.TransactionResolution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 将 v5 客户端挂起事务的回查分发给按事件类型注册的本地事务。
 *
 * <p>注意：业务自定义 {@code Producer} 时必须通过
 * {@code ProducerBuilder#setTransactionChecker(TransactionChecker)} 装配本实现，否则 broker 回查无法
 * 解析事件，事务会持续重试。starter 创建的默认 Producer 已自动装配。
 *
 * @author lipt create on 2026/11/9 10:57
 */
public final class RocketV5TransactionChecker implements TransactionChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(RocketV5TransactionChecker.class);

    private final RocketLocalTransactionRegistry registry;

    /**
     * 创建事务回查分发器。
     *
     * @param registry 按事件类型索引的本地事务
     */
    public RocketV5TransactionChecker(final RocketLocalTransactionRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public TransactionResolution check(final MessageView messageView) {
        try {
            final RocketTransactionEvent event = RocketV5TransactionChecker.decode(messageView);
            final RocketLocalTransaction transaction = this.registry.find(event.eventType());
            if (transaction == null) {
                LOGGER.warn("No local transaction registered for event type {}", event.eventType());
                return TransactionResolution.UNKNOWN;
            }
            return RocketV5TransactionChecker.toResolution(transaction.check(event));
        } catch (final RuntimeException exception) {
            LOGGER.error("RocketMQ transactional check failed", exception);
            return TransactionResolution.UNKNOWN;
        }
    }

    private static RocketTransactionEvent decode(final MessageView messageView) {
        final ByteBuffer body = messageView.getBody().duplicate();
        final byte[] bytes = new byte[body.remaining()];
        body.get(bytes);
        return RocketTransactionEvent.fromJson(new String(bytes, StandardCharsets.UTF_8));
    }

    private static TransactionResolution toResolution(final RocketLocalTransactionState state) {
        return switch (state) {
            case COMMIT -> TransactionResolution.COMMIT;
            case ROLLBACK -> TransactionResolution.ROLLBACK;
            case UNKNOWN -> TransactionResolution.UNKNOWN;
        };
    }
}
