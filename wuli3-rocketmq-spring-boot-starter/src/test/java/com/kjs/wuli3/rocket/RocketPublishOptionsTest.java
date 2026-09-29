package com.kjs.wuli3.rocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.core.error.ErrorCodeException;

import com.kjs.wuli3.rocket.internal.RocketPublishOptions;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RocketPublishOptionsTest {

    @Test
    void createsIndependentImmutableVariants() {
        final RocketPublishOptions defaults = new RocketPublishOptions();
        final RocketPublishOptions configured =
                defaults.withAsync().withAfterCommit().withOrderKey("order-42");

        assertThat(defaults.async()).isFalse();
        assertThat(defaults.afterCommit()).isFalse();
        assertThat(defaults.orderKey()).isNull();
        assertThat(configured.async()).isTrue();
        assertThat(configured.afterCommit()).isTrue();
        assertThat(configured.orderKey()).isEqualTo("order-42");
    }

    @Test
    void rejectsInvalidDelayAndOrderKey() {
        assertThatThrownBy(() -> new RocketPublishOptions().withDelay(Duration.ZERO))
                .isInstanceOf(ErrorCodeException.class)
                .hasMessage("delay must be positive");
        assertThatThrownBy(() -> new RocketPublishOptions().withOrderKey(" "))
                .isInstanceOf(ErrorCodeException.class)
                .hasMessage("orderKey cannot be blank");
    }
}
