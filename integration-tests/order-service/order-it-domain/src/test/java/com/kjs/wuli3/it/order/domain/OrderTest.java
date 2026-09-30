package com.kjs.wuli3.it.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.it.order.sharedkernel.OrderId;
import org.junit.jupiter.api.Test;

/**
 * 验证订单快照及事件载荷的领域约束。
 *
 * @author 国杨 create on 2026/9/30 17:30
 */
class OrderTest {
    /** 订单及创建载荷保留同一业务标识。 */
    @Test
    void retainsIdentityAndState() {
        final Order order = new Order(new OrderId("order-1"), OrderStatus.CREATED);
        assertThat(order.id().value()).isEqualTo(new OrderCreated("order-1").orderId());
        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
    }

    /** 公共构造边界禁止缺失标识或状态。 */
    @Test
    @SuppressWarnings("NullAway") // 故意从不受空安全检查的调用方传入空值，验证运行时边界。
    void rejectsInvalidSnapshots() {
        assertThatThrownBy(() -> new Order(null, OrderStatus.CREATED)).isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> new Order(new OrderId("order-1"), null)).isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> new OrderCreated("bad/id")).isInstanceOf(ErrorCodeException.class);
    }
}
