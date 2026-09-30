package com.kjs.wuli3.it.order.sharedkernel;

import com.kjs.wuli3.core.assertion.Asserts;
import java.util.regex.Pattern;

/**
 * 集成测试订单标识，同时约束数据库、缓存与消息使用的公共键。
 *
 * @author 国杨 create on 2026/9/30 17:30
 */
public record OrderId(String value) {
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    /** 接收外部订单标识，拒绝空值、超长值和非 ASCII 键字符。 */
    public OrderId(final String value) {
        Asserts.whenNull(value).throwIllegalArgumentException("订单 ID 不能为空");
        Asserts.whenFalse(OrderId.VALID_ID.matcher(value).matches())
                .throwIllegalArgumentException("订单 ID 必须为 1 至 64 位字母、数字、下划线或连字符");
        this.value = value;
    }
}
