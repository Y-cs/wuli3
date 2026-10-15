package com.example.order.app.service.order;

import com.example.order.api.dto.order.OrderStatusDto;
import com.example.order.app.repository.order.OrderQueryRepository;
import org.springframework.stereotype.Service;

/**
 * 编排 Order 查询用例，供 HTTP 与内部 Facade 复用。
 *
 * @author GuoYang create on 2026/10/14 15:30
 */
@Service
public class OrderQueryService {
    private final OrderQueryRepository repository;

    /** 注入查询仓储。 */
    public OrderQueryService(final OrderQueryRepository repository) {
        this.repository = repository;
    }

    /** 查询示例状态，直接返回可复用的传播对象。 */
    public OrderStatusDto status() {
        return this.repository.status();
    }
}
