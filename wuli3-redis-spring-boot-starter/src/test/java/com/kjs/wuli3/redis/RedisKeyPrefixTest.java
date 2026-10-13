package com.kjs.wuli3.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

class RedisKeyPrefixTest {

    @Test
    void normalizesConfiguredPrefixes() {
        assertThat(RedisKeyPrefix.of(null)).isEqualTo(RedisKeyPrefix.NONE);
        assertThat(RedisKeyPrefix.of("  ")).isEqualTo(RedisKeyPrefix.NONE);
        assertThat(RedisKeyPrefix.of(":")).isEqualTo(RedisKeyPrefix.NONE);
        assertThat(RedisKeyPrefix.of(" order-service ").value()).isEqualTo("order-service");
        assertThat(RedisKeyPrefix.of("order-service:").value()).isEqualTo("order-service");
        assertThat(RedisKeyPrefix.of("team:order").value()).isEqualTo("team:order");
    }

    @Test
    void wrapsKeysWithASingleSeparator() {
        assertThat(RedisKeyPrefix.of("order-service").wrap("orders:1")).isEqualTo("order-service:orders:1");
        assertThat(RedisKeyPrefix.of("team:order:").wrap("orders:1")).isEqualTo("team:order:orders:1");
        assertThat(RedisKeyPrefix.NONE.wrap("orders:1")).isEqualTo("orders:1");
    }

    @Test
    void reportsWhetherAPrefixIsConfigured() {
        assertThat(RedisKeyPrefix.of("order-service").isPresent()).isTrue();
        assertThat(RedisKeyPrefix.NONE.isPresent()).isFalse();
    }

    @Test
    void prefixesKeysThroughTheGivenDelegate() {
        final RedisSerializer<String> delegate = new StringRedisSerializer(StandardCharsets.UTF_16);
        final RedisKeyPrefix.KeySerializer serializer =
                new RedisKeyPrefix.KeySerializer(RedisKeyPrefix.of("order-service"), delegate);

        assertThat(serializer.serialize("orders:1"))
                .isEqualTo("order-service:orders:1".getBytes(StandardCharsets.UTF_16));
        assertThat(serializer.serialize(null)).isNull();
        assertThat(serializer.deserialize("order-service:orders:1".getBytes(StandardCharsets.UTF_16)))
                .isEqualTo("order-service:orders:1");
    }
}
