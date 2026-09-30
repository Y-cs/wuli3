package com.kjs.wuli3.it.order.adapter.in.web;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.it.order.api.CreateOrderRequest;
import com.kjs.wuli3.it.order.api.OrderApi;
import com.kjs.wuli3.it.order.api.OrderView;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 将集成测试订单用例适配为 HTTP 接口，由 Web Starter 统一包装响应。
 *
 * @author 国杨 create on 2026/9/30 17:30
 */
@RestController
@RequestMapping("/api/orders")
public final class OrderController {
    private final OrderApi api;

    /** 装配订单输入端口。 */
    public OrderController(final OrderApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    /** 创建订单并返回初始状态。 */
    @PostMapping
    public OrderView create(@RequestBody final CreateOrderRequest request) {
        Asserts.whenNull(request).throwIllegalArgumentException("创建订单请求不能为空");
        return this.api.create(request.id());
    }

    /** 按路径中的标识查询订单。 */
    @GetMapping("/{id}")
    public OrderView find(@PathVariable("id") final String id) {
        return this.api.find(id);
    }
}
