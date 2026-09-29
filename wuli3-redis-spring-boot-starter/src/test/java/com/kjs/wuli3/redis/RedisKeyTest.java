package com.kjs.wuli3.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.kjs.wuli3.core.error.ErrorCodeException;

import com.kjs.wuli3.core.error.ErrorCodeException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RedisKeyTest {

    @Test
    void createsPersistentAndExpiringKeys() {
        final RedisKey persistent = RedisKey.persistent("orders:1");
        final RedisKey expiring = RedisKey.expiring("orders:2", Duration.ofMinutes(5));

        assertThat(persistent.value()).isEqualTo("orders:1");
        assertThat(persistent.timeToLive()).isEmpty();
        assertThat(expiring.value()).isEqualTo("orders:2");
        assertThat(expiring.timeToLive()).contains(Duration.ofMinutes(5));
    }

    @Test
    void rejectsBlankKeysAndInvalidTimeToLive() {
        assertThatThrownBy(() -> RedisKey.persistent(" ")).isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> RedisKey.expiring("orders:1", Duration.ZERO))
                .isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> RedisKey.expiring("orders:1", Duration.ofNanos(999_999)))
                .isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> RedisKey.expiring("orders:1", Duration.ofMillis(-1)))
                .isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> RedisKey.expiring("orders:1", Duration.ofSeconds(Long.MAX_VALUE)))
                .isInstanceOf(ErrorCodeException.class);
    }
}
