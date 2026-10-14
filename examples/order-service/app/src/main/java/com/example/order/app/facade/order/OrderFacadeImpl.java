package com.example.order.app.facade.order;

import com.example.order.api.dto.order.OrderStatusDto;
import com.example.order.api.facade.OrderFacade;
import com.example.order.app.service.order.OrderQueryService;
import org.springframework.stereotype.Service;

/**
 * 实现 Order 内部调用契约；RPC 发布由接入或启动配置负责。
 *
 * @author GuoYang create on 2026/10/14 15:30
 */
@Service
public class OrderFacadeImpl implements OrderFacade {
    private final OrderQueryService service;

    /** 注入共享业务用例。 */
    public OrderFacadeImpl(final OrderQueryService service) {
        this.service = service;
    }

    /** 委托核心服务查询状态，不重复业务编排。 */
    @Override
    public OrderStatusDto status() {
        return this.service.status();
    }
}
