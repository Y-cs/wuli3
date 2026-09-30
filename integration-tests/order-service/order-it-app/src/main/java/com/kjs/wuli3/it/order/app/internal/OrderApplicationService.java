package com.kjs.wuli3.it.order.app.internal;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.it.order.api.OrderApi;
import com.kjs.wuli3.it.order.api.OrderView;
import com.kjs.wuli3.it.order.app.port.out.OrderCache;
import com.kjs.wuli3.it.order.app.port.out.OrderCreationLock;
import com.kjs.wuli3.it.order.app.port.out.OrderEvents;
import com.kjs.wuli3.it.order.app.port.out.OrderRepository;
import com.kjs.wuli3.it.order.domain.Order;
import com.kjs.wuli3.it.order.domain.OrderCreated;
import com.kjs.wuli3.it.order.domain.OrderStatus;
import com.kjs.wuli3.it.order.sharedkernel.OrderErrors;
import com.kjs.wuli3.it.order.sharedkernel.OrderId;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 编排订单创建事务、提交后事件注册及缓存查询。
 *
 * @author 国杨 create on 2026/9/30 17:30
 */
@Service
public final class OrderApplicationService implements OrderApi {
    private final OrderRepository repository;
    private final OrderCache cache;
    private final OrderCreationLock lock;
    private final OrderEvents events;
    private final TransactionTemplate transactions;

    /** 装配应用端口与事务管理器；依赖缺失属于启动配置错误。 */
    public OrderApplicationService(
            final OrderRepository repository,
            final OrderCache cache,
            final OrderCreationLock lock,
            final OrderEvents events,
            final PlatformTransactionManager transactionManager) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.lock = Objects.requireNonNull(lock, "lock");
        this.events = Objects.requireNonNull(events, "events");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
    }

    /** 加锁后保存订单并注册事件；事务采用默认 REQUIRED 传播语义。 */
    @Override
    public OrderView create(final String id) {
        final OrderId orderId = new OrderId(id);
        return this.lock.execute(
                orderId,
                () -> Objects.requireNonNull(this.transactions.execute(status -> {
                    final Order order = new Order(orderId, OrderStatus.CREATED);
                    this.repository.save(order);
                    this.events.created(new OrderCreated(orderId.value()));
                    return OrderApplicationService.view(order);
                })));
    }

    /** 查询缓存或数据库，只缓存实际存在的订单。 */
    @Override
    public OrderView find(final String id) {
        final OrderId orderId = new OrderId(id);
        // 外层事务可能读到尚未提交的订单，禁止其进入事务之外的共享缓存。
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return OrderApplicationService.view(this.repository
                    .find(orderId)
                    .orElseThrow(() -> new ErrorCodeException(OrderErrors.ORDER_NOT_FOUND)));
        }
        final Order order = this.cache.find(orderId).orElseGet(() -> {
            final Order stored = this.repository
                    .find(orderId)
                    .orElseThrow(() -> new ErrorCodeException(OrderErrors.ORDER_NOT_FOUND));
            this.cache.put(stored);
            return stored;
        });
        return OrderApplicationService.view(order);
    }

    /** 将领域快照转换为稳定的外部视图。 */
    private static OrderView view(final Order order) {
        return new OrderView(order.id().value(), order.status().name());
    }
}
