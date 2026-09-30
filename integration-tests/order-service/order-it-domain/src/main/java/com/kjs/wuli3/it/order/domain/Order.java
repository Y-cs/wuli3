package com.kjs.wuli3.it.order.domain;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.it.order.sharedkernel.OrderId;

/**
 * 不可变订单聚合，保存已创建订单的标识与状态。
 *
 * @author 国杨 create on 2026/9/30 17:30
 */
public record Order(OrderId id, OrderStatus status) {
    /** 构造订单快照并校验公共调用边界。 */
    public Order(final OrderId id, final OrderStatus status) {
        Asserts.whenNull(id).throwIllegalArgumentException("订单 ID 不能为空");
        Asserts.whenNull(status).throwIllegalArgumentException("订单状态不能为空");
        this.id = id;
        this.status = status;
    }
}
