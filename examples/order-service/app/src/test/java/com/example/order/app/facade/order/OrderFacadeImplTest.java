package com.example.order.app.facade.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.order.api.dto.order.OrderStatusDto;
import com.example.order.app.service.order.OrderQueryService;
import com.example.order.sharedkernel.EntityId;
import com.example.order.sharedkernel.Status;
import org.junit.jupiter.api.Test;

/**
 * 验证入口复用应用查询服务并保持结果契约。
 *
 * @author GuoYang create on 2026/10/14 15:30
 */
final class OrderFacadeImplTest {
    /** 入口直接返回共享用例结果，不重复转换或业务编排。 */
    @Test
    void delegatesToQueryService() {
        final var expected = new OrderStatusDto(new EntityId("sample-1"), Status.ACTIVE);
        final var service = new OrderQueryService(() -> expected);
        final var entry = new OrderFacadeImpl(service);

        assertThat(entry.status()).isSameAs(expected);
    }
}
