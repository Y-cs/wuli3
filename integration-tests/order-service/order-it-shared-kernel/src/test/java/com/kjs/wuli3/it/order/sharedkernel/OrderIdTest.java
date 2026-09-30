package com.kjs.wuli3.it.order.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import org.junit.jupiter.api.Test;

/**
 * 验证订单标识输入边界与业务错误归属。
 *
 * @author 国杨 create on 2026/9/30 17:30
 */
class OrderIdTest {
    /** 接受合法 ASCII 标识及长度上界，不隐式改写调用方标识。 */
    @Test
    void acceptsValidIds() {
        assertThat(new OrderId("Order_123-ABC").value()).isEqualTo("Order_123-ABC");
        assertThat(new OrderId("a".repeat(64)).value()).hasSize(64);
    }

    /** 非法标识均进入统一非法参数错误链。 */
    @Test
    void rejectsInvalidIds() {
        for (final String id : new String[] {null, "", " ", "a b", "a/b", "订单", "a".repeat(65)}) {
            assertThatThrownBy(() -> new OrderId(id))
                    .isInstanceOfSatisfying(
                            ErrorCodeException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrors.ILLEGAL_ARGUMENT));
        }
    }

    /** 订单业务错误可安全作为调用方错误传播。 */
    @Test
    void declaresCallerErrors() {
        for (final OrderErrors error : OrderErrors.values()) {
            assertThat(new ErrorCodeException(error).getOrigin()).isEqualTo(ErrorOrigin.CALLER);
        }
    }
}
