package com.kjs.wuli3.it.order.infra.lock;

import com.kjs.wuli3.it.order.app.port.out.OrderCreationLock;
import com.kjs.wuli3.it.order.sharedkernel.OrderId;
import com.kjs.wuli3.redis.lock.RedisLock;
import com.kjs.wuli3.redis.lock.RedisLockExecutor;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 按订单 ID 互斥执行创建用例，watchdog 在事务完成前持续续租。
 *
 * @author 国杨 create on 2026/9/30 10:00
 */
@Component
public final class RedisOrderCreationLock implements OrderCreationLock {
    private final RedisLockExecutor executor;
    private final String resourcePrefix;

    /** 注入锁执行器和资源前缀。 */
    public RedisOrderCreationLock(
            final RedisLockExecutor executor,
            @Value("${it.order.resource-prefix:order-it}") final String resourcePrefix) {
        this.executor = executor;
        this.resourcePrefix = resourcePrefix;
    }

    /** 最多等待 5 秒获取锁，任务结束或抛错后释放。 */
    @Override
    public <T> T execute(final OrderId id, final Supplier<T> action) {
        return this.executor.execute(
                RedisLock.watchdog(this.resourcePrefix + ":lock:" + id.value(), Duration.ofSeconds(5)), action);
    }
}
