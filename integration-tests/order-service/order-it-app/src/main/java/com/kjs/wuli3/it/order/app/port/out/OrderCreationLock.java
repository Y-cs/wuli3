package com.kjs.wuli3.it.order.app.port.out;

import com.kjs.wuli3.it.order.sharedkernel.OrderId;
import java.util.function.Supplier;

/**
 * 按订单标识互斥执行创建操作的输出端口。
 *
 * @author 国杨 create on 2026/9/30 17:30
 */
public interface OrderCreationLock {
    /** 持锁执行回调，并在正常返回或异常时释放锁。 */
    <T> T execute(OrderId id, Supplier<T> action);
}
