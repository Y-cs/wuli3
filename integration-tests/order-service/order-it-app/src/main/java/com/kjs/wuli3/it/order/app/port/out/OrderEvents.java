package com.kjs.wuli3.it.order.app.port.out;

import com.kjs.wuli3.it.order.domain.OrderCreated;

/**
 * 订单事件输出端口，将业务载荷交给事务提交后发送机制。
 *
 * @author 国杨 create on 2026/9/30 17:30
 */
public interface OrderEvents {
    /** 注册订单创建事件；当前事务回滚时不得发送。 */
    void created(OrderCreated event);
}
