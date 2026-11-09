package com.kjs.wuli3.rocket.transaction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 按事件类型索引全部 {@link RocketLocalTransaction}。
 *
 * @author lipt create on 2026/11/9 10:57
 */
public final class RocketLocalTransactionRegistry {

    private final Map<String, RocketLocalTransaction> transactions;

    /**
     * 创建注册表；同一事件类型重复注册时立即失败。
     *
     * @param transactions 容器中全部本地事务实现
     */
    public RocketLocalTransactionRegistry(final List<RocketLocalTransaction> transactions) {
        Objects.requireNonNull(transactions, "transactions");
        final Map<String, RocketLocalTransaction> registered = new HashMap<>();
        for (final RocketLocalTransaction transaction : transactions) {
            final String eventType = transaction.eventType();
            if (registered.putIfAbsent(eventType, transaction) != null) {
                throw new IllegalStateException("Duplicate local transaction for event type " + eventType);
            }
        }
        this.transactions = Map.copyOf(registered);
    }

    /**
     * 查找事件类型对应的本地事务。
     *
     * @param eventType 事件类型
     * @return 已注册的本地事务；未注册时返回 null
     */
    public @Nullable RocketLocalTransaction find(final String eventType) {
        return this.transactions.get(eventType);
    }
}
