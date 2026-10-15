package com.example.order.app.repository.order;

import com.example.order.api.dto.order.OrderStatusDto;

/**
 * Order 查询投影仓储；查询不要求还原领域聚合。
 *
 * @author GuoYang create on 2026/10/14 15:30
 */
public interface OrderQueryRepository {
    /** 读取示例状态投影。 */
    OrderStatusDto status();
}
