package com.example.order.api.facade;

import com.example.order.api.dto.order.OrderStatusDto;

/**
 * Order 业务能力的内部 RPC 契约。
 *
 * @author GuoYang create on 2026/9/7 15:30
 */
public interface OrderFacade {
    /** 查询示例聚合的状态。 */
    OrderStatusDto status();
}
