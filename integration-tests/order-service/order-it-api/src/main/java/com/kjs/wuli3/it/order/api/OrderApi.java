package com.kjs.wuli3.it.order.api;

/**
 * 订单集成验收服务的创建与查询输入契约。
 *
 * @author 国杨 create on 2026/9/30 17:30
 */
public interface OrderApi {
    /**
     * 创建订单并在事务提交后发布事件。
     *
     * <p>通常从无事务的入站适配器调用，此时锁覆盖整个创建事务。若调用方已有事务，则参与该事务，
     * 锁仅覆盖本方法执行，外层提交之前的并发安全最终由数据库唯一约束保证。
     */
    OrderView create(String id);

    /** 查询订单，优先读取缓存；不存在时抛出订单不存在错误。 */
    OrderView find(String id);
}
