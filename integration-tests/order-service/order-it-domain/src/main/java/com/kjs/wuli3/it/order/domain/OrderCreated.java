package com.kjs.wuli3.it.order.domain;

import com.kjs.wuli3.it.order.sharedkernel.OrderId;

/**
 * 订单创建事件的业务载荷；事件标识与类型由集成事件信封承载。
 *
 * @author 国杨 create on 2026/9/30 17:30
 */
public record OrderCreated(String orderId) {
    /** 校验事件载荷使用与订单一致的标识规则。 */
    public OrderCreated(final String orderId) {
        this.orderId = new OrderId(orderId).value();
    }
}
