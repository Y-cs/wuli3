package com.example.order.infra.persistence.order;

import com.example.order.domain.order.Order;
import com.example.order.domain.order.OrderRepository;
import com.example.order.infra.persistence.order.entity.OrderPo;
import com.example.order.infra.persistence.order.mapper.OrderMapper;
import com.example.order.sharedkernel.EntityId;
import org.springframework.stereotype.Repository;

/**
 * Order 聚合仓储的基础设施实现。
 *
 * @author GuoYang create on 2026/9/7 15:30
 */
@Repository
public class OrderRepositoryImpl implements OrderRepository {
    private final OrderMapper mapper;

    public OrderRepositoryImpl(final OrderMapper mapper) {
        this.mapper = mapper;
    }

    /** 加载示例聚合。 */
    @Override
    public Order load() {
        final OrderPo stored = this.mapper.selectById("sample-1");
        if (stored == null) {
            return new Order(new EntityId("sample-1"));
        }
        return stored.toDomain();
    }
}
