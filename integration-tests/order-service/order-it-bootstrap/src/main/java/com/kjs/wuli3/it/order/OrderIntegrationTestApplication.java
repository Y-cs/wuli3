package com.kjs.wuli3.it.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 组装专用于真实基础设施验收的订单服务。
 *
 * @author 国杨 create on 2026/9/30 10:00
 */
@SpringBootApplication
public class OrderIntegrationTestApplication {
    /** 启动集成测试夹具，可单独运行以辅助排查。 */
    public static void main(final String[] args) {
        SpringApplication.run(OrderIntegrationTestApplication.class, args);
    }
}
