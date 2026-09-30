package com.kjs.wuli3.rocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.rocket.v4.RocketV4PublishOptions;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 验证发布选项的快照隔离与参数约束。
 *
 * @author GuoYang create on 2026/9/30 10:00
 */
class RocketV4PublishOptionsTest {

    /** 验证构建器复用不会修改已构建选项。 */
    @Test
    void builderProducesIndependentSnapshots() {
        final RocketV4PublishOptions.Builder builder = RocketV4PublishOptions.builder();
        assertThat(builder.build()).isEqualTo(new RocketV4PublishOptions());
        assertThat(builder.async(true)).isSameAs(builder);
        assertThat(builder.afterCommit(true)).isSameAs(builder);
        assertThat(builder.orderKey("order-42")).isSameAs(builder);
        final RocketV4PublishOptions first = builder.build();
        final RocketV4PublishOptions second =
                builder.orderKey("order-43").async(false).build();
        assertThat(first.async()).isTrue();
        assertThat(first.afterCommit()).isTrue();
        assertThat(first.orderKey()).isEqualTo("order-42");
        assertThat(second.async()).isFalse();
        assertThat(second.orderKey()).isEqualTo("order-43");
    }

    /** 验证构建器沿用构造器校验并保留回调引用。 */
    @Test
    void builderValidatesAndPreservesCallback() {
        assertThatThrownBy(() ->
                        RocketV4PublishOptions.builder().delay(Duration.ZERO).build())
                .isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> RocketV4PublishOptions.builder().orderKey(" ").build())
                .isInstanceOf(ErrorCodeException.class);
        final org.apache.rocketmq.client.producer.SendCallback callback =
                org.mockito.Mockito.mock(org.apache.rocketmq.client.producer.SendCallback.class);
        final RocketV4PublishOptions options = RocketV4PublishOptions.builder()
                .async(true)
                .sendCallback(callback)
                .build();
        assertThat(options.async()).isTrue();
        assertThat(options.sendCallback()).isSameAs(callback);
        assertThat(RocketV4PublishOptions.builder()
                        .sendCallback(callback)
                        .build()
                        .async())
                .isFalse();
    }

    @Test
    void createsIndependentImmutableVariants() {
        final RocketV4PublishOptions defaults = new RocketV4PublishOptions();
        final RocketV4PublishOptions configured = RocketV4PublishOptions.builder()
                .async(true)
                .afterCommit(true)
                .orderKey("order-42")
                .build();

        assertThat(defaults.async()).isFalse();
        assertThat(defaults.afterCommit()).isFalse();
        assertThat(defaults.orderKey()).isNull();
        assertThat(configured.async()).isTrue();
        assertThat(configured.afterCommit()).isTrue();
        assertThat(configured.orderKey()).isEqualTo("order-42");
    }

    @Test
    void rejectsInvalidDelayAndOrderKey() {
        assertThatThrownBy(() ->
                        RocketV4PublishOptions.builder().delay(Duration.ZERO).build())
                .isInstanceOf(ErrorCodeException.class)
                .hasMessage("delay must be positive");
        assertThatThrownBy(() -> RocketV4PublishOptions.builder().orderKey(" ").build())
                .isInstanceOf(ErrorCodeException.class)
                .hasMessage("orderKey cannot be blank");
    }
}
