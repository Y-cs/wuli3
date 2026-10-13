package com.kjs.wuli3.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kjs.wuli3.redis.codec.JsonRedisCodec;
import com.kjs.wuli3.redis.codec.RedisCodec;
import com.kjs.wuli3.redis.operation.HashRedisOperations;
import com.kjs.wuli3.redis.operation.ObjectRedisOperations;
import com.kjs.wuli3.redis.operation.SetRedisOperations;
import com.kjs.wuli3.redis.operation.StringRedisOperations;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@ExtendWith(MockitoExtension.class)
class RedisSupportTest {

    @Mock
    private RedisConnectionFactory connectionFactory;

    private RedisSupport redisSupport;

    @BeforeEach
    void setUp() {
        this.redisSupport =
                new RedisSupport(this.connectionFactory, JsonRedisCodec.INSTANCE, RedisKeyPrefix.of("order-service"));
    }

    @Test
    void exposesAllDataStructureOperations() {
        assertThat(this.redisSupport.stringOperations()).isInstanceOf(StringRedisOperations.class);
        assertThat(this.redisSupport.objectOperations()).isInstanceOf(ObjectRedisOperations.class);
        assertThat(this.redisSupport.hashOperations()).isInstanceOf(HashRedisOperations.class);
        assertThat(this.redisSupport.setOperations()).isInstanceOf(SetRedisOperations.class);
    }

    @Test
    void buildsItsOwnTemplateOnTheGivenConnectionFactoryWithPrefixedKeys() {
        final RedisTemplate<String, String> template = this.redisSupport.redisTemplate();

        assertThat(template.getConnectionFactory()).isSameAs(this.connectionFactory);
        assertThat(RedisSupportTest.keySerializer(template).serialize("orders:1"))
                .isEqualTo("order-service:orders:1".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void installsTheKeyAndFieldSerializersProvidedByTheCodec() {
        final RedisSerializer<String> codecKeySerializer = new StringRedisSerializer(StandardCharsets.UTF_16);
        final RedisSerializer<String> hashKeySerializer = new StringRedisSerializer(StandardCharsets.UTF_16);
        final RedisCodec codec = mock(RedisCodec.class);
        when(codec.keySerializer(any(RedisKeyPrefix.class))).thenReturn(codecKeySerializer);
        when(codec.hashKeySerializer()).thenReturn(hashKeySerializer);

        final RedisTemplate<String, String> template =
                new RedisSupport(this.connectionFactory, codec, RedisKeyPrefix.of("order-service")).redisTemplate();

        assertThat(template.getKeySerializer()).isSameAs(codecKeySerializer);
        assertThat(template.getHashKeySerializer()).isSameAs(hashKeySerializer);
        assertThat(template.getValueSerializer()).isSameAs(StringRedisSerializer.UTF_8);
        assertThat(template.getHashValueSerializer()).isSameAs(StringRedisSerializer.UTF_8);
        verify(codec).keySerializer(RedisKeyPrefix.of("order-service"));
    }

    @Test
    void keepsTheCodecAvailableToStructuredOperations() {
        assertThat(this.redisSupport.codec()).isSameAs(JsonRedisCodec.INSTANCE);
    }

    @Test
    void rejectsExpiringPersistentKeys() {
        assertThatThrownBy(() -> this.redisSupport.expire(RedisKey.persistent("orders:1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings("unchecked")
    private static RedisSerializer<String> keySerializer(final RedisTemplate<String, String> template) {
        return (RedisSerializer<String>) Objects.requireNonNull(template.getKeySerializer(), "keySerializer");
    }
}
