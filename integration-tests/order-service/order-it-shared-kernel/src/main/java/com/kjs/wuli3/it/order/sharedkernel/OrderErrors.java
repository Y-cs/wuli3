package com.kjs.wuli3.it.order.sharedkernel;

import com.kjs.wuli3.core.error.model.ErrorCode;
import com.kjs.wuli3.core.error.model.ErrorMetadata;
import com.kjs.wuli3.core.error.model.ErrorModule;
import com.kjs.wuli3.core.error.model.ErrorOrigin;

/**
 * 订单集成验收的稳定业务错误码。
 *
 * @author 国杨 create on 2026/9/30 17:30
 */
@ErrorModule(name = "ORDER_IT", defaultMetadata = @ErrorMetadata(origin = ErrorOrigin.CALLER))
public enum OrderErrors implements ErrorCode {
    DUPLICATE_ORDER("订单已存在"),
    ORDER_NOT_FOUND("订单不存在");

    private final String message;

    OrderErrors(final String message) {
        this.message = message;
    }

    /** 返回可向调用方展示的默认错误信息。 */
    @Override
    public String getMessage() {
        return this.message;
    }
}
