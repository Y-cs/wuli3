package com.kjs.wuli3.it.order.infra.persistence;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.it.order.app.port.out.OrderRepository;
import com.kjs.wuli3.it.order.domain.Order;
import com.kjs.wuli3.it.order.sharedkernel.OrderErrors;
import com.kjs.wuli3.it.order.sharedkernel.OrderId;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * 使用真实 MySQL 实现订单存储并转换唯一约束错误。
 *
 * @author 国杨 create on 2026/9/30 10:00
 */
@Repository
public class MybatisOrderRepository implements OrderRepository {
    private final OrderMapper mapper;

    /** 注入订单 Mapper。 */
    public MybatisOrderRepository(final OrderMapper mapper) {
        this.mapper = mapper;
    }

    /** 新增订单，重复主键进入统一业务错误链。 */
    @Override
    public void save(final Order order) {
        try {
            this.mapper.insert(new OrderPo(order));
        } catch (final DuplicateKeyException exception) {
            throw new ErrorCodeException(OrderErrors.DUPLICATE_ORDER, exception);
        }
    }

    /** 查找订单，不存在时返回空值。 */
    @Override
    public Optional<Order> find(final OrderId id) {
        return Optional.ofNullable(this.mapper.selectById(id.value())).map(OrderPo::toDomain);
    }
}
