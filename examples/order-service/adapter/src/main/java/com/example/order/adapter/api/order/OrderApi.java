package com.example.order.adapter.api.order;

import com.example.order.api.dto.order.OrderStatusDto;
import com.example.order.app.service.order.OrderQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 将 Order 应用契约适配为 HTTP 接口。
 *
 * @author GuoYang create on 2026/9/7 15:30
 */
@RestController
@RequestMapping("/api/order")
public final class OrderApi {
    private final OrderQueryService service;

    /** 注入共享业务用例。 */
    public OrderApi(final OrderQueryService service) {
        this.service = service;
    }

    /** 查询示例聚合状态。 */
    @GetMapping("/status")
    public OrderStatusDto status() {
        return this.service.status();
    }
}
