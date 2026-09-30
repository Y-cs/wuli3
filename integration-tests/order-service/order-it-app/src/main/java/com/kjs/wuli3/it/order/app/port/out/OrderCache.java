package com.kjs.wuli3.it.order.app.port.out;

import com.kjs.wuli3.it.order.domain.Order;
import com.kjs.wuli3.it.order.sharedkernel.OrderId;
import java.util.Optional;

/**
 * 订单查询缓存输出端口；过期策略由基础设施实现负责。
 *
 * @author 国杨 create on 2026/9/30 17:30
 */
public interface OrderCache {
    /** 读取缓存快照，未命中或已过期时返回空。 */
    Optional<Order> find(OrderId id);

    /** 写入订单快照并设置约定的有效期。 */
    void put(Order order);
}
