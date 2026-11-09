package com.kjs.wuli3.rocket.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RocketLocalTransactionRegistryTest {

    /** 验证按事件类型索引并拒绝重复注册。 */
    @Test
    void indexesByEventTypeAndRejectsDuplicates() {
        final RocketLocalTransaction registered = RocketLocalTransactionRegistryTest.transaction("order.paid.v1");
        final RocketLocalTransactionRegistry registry = new RocketLocalTransactionRegistry(List.of(registered));

        assertThat(registry.find("order.paid.v1")).isSameAs(registered);
        assertThat(registry.find("order.cancelled.v1")).isNull();
        assertThatThrownBy(() -> new RocketLocalTransactionRegistry(
                        List.of(registered, RocketLocalTransactionRegistryTest.transaction("order.paid.v1"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate local transaction");
    }

    private static RocketLocalTransaction transaction(final String eventType) {
        return new RocketLocalTransaction() {
            @Override
            public String eventType() {
                return eventType;
            }

            @Override
            public RocketLocalTransactionState execute(final RocketTransactionEvent event) {
                return RocketLocalTransactionState.COMMIT;
            }

            @Override
            public RocketLocalTransactionState check(final RocketTransactionEvent event) {
                return RocketLocalTransactionState.COMMIT;
            }
        };
    }
}
