package com.kjs.wuli3.it.order.app.port.out;

import com.kjs.wuli3.it.order.domain.Order;
import com.kjs.wuli3.it.order.sharedkernel.OrderId;
import java.util.Optional;

/**
 * 订单持久化输出端口；重复标识须转换为订单已存在错误。
 *
 * @author 国杨 create on 2026/9/30 17:30
 */
public interface OrderRepository {
    /** 保存新订单，禁止覆盖已有订单。 */
    void save(Order order);

    /** 按标识读取订单，不存在时返回空。 */
    Optional<Order> find(OrderId id);
}
