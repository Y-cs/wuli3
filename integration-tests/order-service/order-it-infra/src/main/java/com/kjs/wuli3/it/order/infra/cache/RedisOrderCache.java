package com.kjs.wuli3.it.order.infra.cache;

import com.kjs.wuli3.it.order.app.port.out.OrderCache;
import com.kjs.wuli3.it.order.domain.Order;
import com.kjs.wuli3.it.order.domain.OrderStatus;
import com.kjs.wuli3.it.order.sharedkernel.OrderId;
import com.kjs.wuli3.redis.RedisKey;
import com.kjs.wuli3.redis.RedisSupport;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 使用独立资源前缀缓存订单快照，缓存有效期为 60 秒。
 *
 * @author 国杨 create on 2026/9/30 10:00
 */
@Component
public final class RedisOrderCache implements OrderCache {
    private final RedisSupport redis;
    private final String resourcePrefix;

    /** 注入 Redis 操作与本次验收资源前缀。 */
    public RedisOrderCache(
            final RedisSupport redis, @Value("${it.order.resource-prefix:order-it}") final String resourcePrefix) {
        this.redis = redis;
        this.resourcePrefix = resourcePrefix;
    }

    /** 查找缓存；未命中时由应用层决定是否查询数据库。 */
    @Override
    public Optional<Order> find(final OrderId id) {
        return this.redis
                .objectOperations()
                .get(this.key(id), CachedOrder.class)
                .map(value -> new Order(new OrderId(value.id()), OrderStatus.valueOf(value.status())));
    }

    /** 写入仅包含稳定标量的缓存快照。 */
    @Override
    public void put(final Order order) {
        this.redis
                .objectOperations()
                .set(
                        this.key(order.id()),
                        new CachedOrder(order.id().value(), order.status().name()));
    }

    private RedisKey key(final OrderId id) {
        return RedisKey.expiring(this.resourcePrefix + ":order:" + id.value(), Duration.ofSeconds(60));
    }

    /**
     * 隔离 Redis 序列化协议与领域对象结构。
     *
     * @author 国杨 create on 2026/9/30 10:00
     */
    public record CachedOrder(String id, String status) {}
}
