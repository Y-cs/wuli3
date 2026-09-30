package com.kjs.wuli3.it.order.app.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.core.error.ErrorCodeException;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.lang.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * 验证应用编排顺序；真实提交、回滚与消息投递由验收模块覆盖。
 *
 * @author 国杨 create on 2026/9/30 17:30
 */
class OrderApplicationServiceTest {
    /** 锁覆盖应用事务提交，且事件注册发生在持久化之后。 */
    @Test
    void holdsLockThroughCommitAndRegistersEventAfterSave() {
        final Fixture fixture = new Fixture();
        assertThat(fixture.service.create("order-1")).isEqualTo(new OrderView("order-1", "CREATED"));
        assertThat(fixture.calls).containsExactly("lock", "begin", "save", "event:order-1", "commit", "unlock");
    }

    /** 事件注册失败会触发回滚并释放锁。 */
    @Test
    void rollsBackAndReleasesLockWhenEventRegistrationFails() {
        final Fixture fixture = new Fixture();
        fixture.failEvent = true;
        assertThatThrownBy(() -> fixture.service.create("order-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("event registration failed");
        assertThat(fixture.calls).containsExactly("lock", "begin", "save", "event:order-1", "rollback", "unlock");
    }

    /** 重复订单错误不注册事件且仍回滚并释放锁。 */
    @Test
    void propagatesDuplicateOrderAndDoesNotRegisterEvent() {
        final Fixture fixture = new Fixture();
        fixture.stored.put(new OrderId("order-1"), new Order(new OrderId("order-1"), OrderStatus.CREATED));
        assertThatThrownBy(() -> fixture.service.create("order-1"))
                .isInstanceOfSatisfying(
                        ErrorCodeException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(OrderErrors.DUPLICATE_ORDER));
        assertThat(fixture.calls).containsExactly("lock", "begin", "save", "rollback", "unlock");
    }

    /** 缓存未命中时回源并回填，下一次查询不再访问数据库。 */
    @Test
    void populatesCacheAndThenServesHit() {
        final Fixture fixture = new Fixture();
        final Order order = new Order(new OrderId("order-1"), OrderStatus.CREATED);
        fixture.stored.put(order.id(), order);
        assertThat(fixture.service.find("order-1")).isEqualTo(new OrderView("order-1", "CREATED"));
        assertThat(fixture.service.find("order-1")).isEqualTo(new OrderView("order-1", "CREATED"));
        assertThat(fixture.calls).containsExactly("cache-find", "repository-find", "cache-put", "cache-find");
    }

    /** 不存在的订单返回业务错误并保持缓存为空。 */
    @Test
    void doesNotCacheMissingOrders() {
        final Fixture fixture = new Fixture();
        assertThatThrownBy(() -> fixture.service.find("missing"))
                .isInstanceOfSatisfying(
                        ErrorCodeException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(OrderErrors.ORDER_NOT_FOUND));
        assertThat(fixture.calls).containsExactly("cache-find", "repository-find");
        assertThat(fixture.cached).isEmpty();
    }

    /** 参数校验先于锁、事务和缓存等基础设施调用。 */
    @Test
    void validatesBeforeCallingPorts() {
        final Fixture fixture = new Fixture();
        assertThatThrownBy(() -> fixture.service.create("bad/id")).isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> fixture.service.find(" ")).isInstanceOf(ErrorCodeException.class);
        assertThat(fixture.calls).isEmpty();
    }

    private static final class Fixture
            implements OrderRepository, OrderCreationLock, OrderEvents, PlatformTransactionManager {
        private final List<String> calls = new ArrayList<>();
        private final Map<OrderId, Order> stored = new HashMap<>();
        private final Map<OrderId, Order> cached = new HashMap<>();
        private boolean failEvent;
        private final OrderApplicationService service = new OrderApplicationService(
                this,
                new OrderCache() {
                    @Override
                    public Optional<Order> find(final OrderId id) {
                        Fixture.this.calls.add("cache-find");
                        return Optional.ofNullable(Fixture.this.cached.get(id));
                    }

                    @Override
                    public void put(final Order order) {
                        Fixture.this.calls.add("cache-put");
                        Fixture.this.cached.put(order.id(), order);
                    }
                },
                this,
                this,
                this);

        @Override
        public void save(final Order order) {
            this.calls.add("save");
            if (this.stored.putIfAbsent(order.id(), order) != null) {
                throw new ErrorCodeException(OrderErrors.DUPLICATE_ORDER);
            }
        }

        @Override
        public Optional<Order> find(final OrderId id) {
            this.calls.add("repository-find");
            return Optional.ofNullable(this.stored.get(id));
        }

        @Override
        public <T> T execute(final OrderId id, final Supplier<T> action) {
            this.calls.add("lock");
            try {
                return action.get();
            } finally {
                this.calls.add("unlock");
            }
        }

        @Override
        public void created(final OrderCreated event) {
            this.calls.add("event:" + event.orderId());
            if (this.failEvent) {
                throw new IllegalStateException("event registration failed");
            }
        }

        @Override
        public TransactionStatus getTransaction(final @Nullable TransactionDefinition definition) {
            this.calls.add("begin");
            final TransactionDefinition effectiveDefinition =
                    definition == null ? TransactionDefinition.withDefaults() : definition;
            assertThat(effectiveDefinition.getPropagationBehavior())
                    .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(final TransactionStatus status) {
            this.calls.add("commit");
        }

        @Override
        public void rollback(final TransactionStatus status) {
            this.calls.add("rollback");
        }
    }
}
