package com.kjs.wuli3.it.order.infra.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kjs.wuli3.it.order.domain.Order;
import com.kjs.wuli3.it.order.domain.OrderStatus;
import com.kjs.wuli3.it.order.sharedkernel.OrderId;

/**
 * 映射集成测试订单表；可变字段仅用于 MyBatis 反射绑定。
 *
 * @author 国杨 create on 2026/9/30 10:00
 */
@TableName("it_orders")
public final class OrderPo {
    @TableId(type = IdType.INPUT)
    private String id = "";

    private String status = "";

    /** 为 MyBatis 创建待绑定的持久化对象。 */
    public OrderPo() {}

    /** 从领域订单创建持久化快照。 */
    public OrderPo(final Order order) {
        this.id = order.id().value();
        this.status = order.status().name();
    }

    /** 返回数据库主键。 */
    public String getId() {
        return this.id;
    }

    /** 返回数据库状态值。 */
    public String getStatus() {
        return this.status;
    }

    /** 重建已持久化的订单。 */
    public Order toDomain() {
        return new Order(new OrderId(this.id), OrderStatus.valueOf(this.status));
    }
}
