package com.example.order.infra.persistence.order;

import com.example.order.api.dto.order.OrderStatusDto;
import com.example.order.app.repository.order.OrderQueryRepository;
import com.example.order.infra.persistence.order.entity.OrderPo;
import com.example.order.infra.persistence.order.mapper.OrderMapper;
import com.example.order.sharedkernel.EntityId;
import com.example.order.sharedkernel.Status;
import org.springframework.stereotype.Repository;

/**
 * 直接读取 Order 查询投影，不借用聚合仓储还原领域模型。
 *
 * @author GuoYang create on 2026/10/14 15:30
 */
@Repository
public class OrderQueryRepositoryImpl implements OrderQueryRepository {
    private final OrderMapper mapper;

    public OrderQueryRepositoryImpl(final OrderMapper mapper) {
        this.mapper = mapper;
    }

    /** 读取示例状态投影。 */
    @Override
    public OrderStatusDto status() {
        final OrderPo stored = this.mapper.selectById("sample-1");
        final String id = stored == null ? "sample-1" : stored.id();
        return new OrderStatusDto(new EntityId(id), Status.ACTIVE);
    }
}
