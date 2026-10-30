package com.kjs.wuli3.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kjs.wuli3.redis.autoconfigure.RedisAutoConfiguration;
import com.kjs.wuli3.redis.autoconfigure.RedisProperties;
import com.kjs.wuli3.redis.codec.JsonRedisCodec;
import com.kjs.wuli3.redis.codec.RedisCodec;
import com.kjs.wuli3.redis.lock.RedisLockExecutor;
import com.kjs.wuli3.redis.operation.HashRedisOperations;
import com.kjs.wuli3.redis.operation.ObjectRedisOperations;
import com.kjs.wuli3.redis.operation.SetRedisOperations;
import com.kjs.wuli3.redis.operation.StringRedisOperations;
import com.kjs.wuli3.spring.autoconfigure.CoreSpringBootAutoConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

class RedisAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(CoreSpringBootAutoConfiguration.class, RedisAutoConfiguration.class))
            .withPropertyValues("spring.application.name=order-service");

    @Test
    void createsDefaultInfrastructureBeansWhenClientsAreAvailable() {
        this.contextRunner
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisProperties.class);
                    assertThat(context).hasSingleBean(RedisSupport.class);
                    assertThat(context).hasSingleBean(RedisCodec.class);
                    assertThat(context.getBean(RedisCodec.class)).isSameAs(JsonRedisCodec.INSTANCE);
                    assertThat(context).doesNotHaveBean(StringRedisOperations.class);
                    assertThat(context).doesNotHaveBean(ObjectRedisOperations.class);
                    assertThat(context).doesNotHaveBean(HashRedisOperations.class);
                    assertThat(context).doesNotHaveBean(SetRedisOperations.class);
                    final RedisSupport redisSupport = context.getBean(RedisSupport.class);
                    assertThat(redisSupport.stringOperations()).isInstanceOf(StringRedisOperations.class);
                    assertThat(redisSupport.objectOperations()).isInstanceOf(ObjectRedisOperations.class);
                    assertThat(redisSupport.hashOperations()).isInstanceOf(HashRedisOperations.class);
                    assertThat(redisSupport.setOperations()).isInstanceOf(SetRedisOperations.class);
                    assertThat(context).hasSingleBean(RedisLockExecutor.class);
                });
    }

    @Test
    void doesNotCreateInfrastructureBeansWithoutRequiredClients() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RedisProperties.class);
            assertThat(context).doesNotHaveBean(RedisSupport.class);
            assertThat(context).doesNotHaveBean(StringRedisOperations.class);
            assertThat(context).doesNotHaveBean(ObjectRedisOperations.class);
            assertThat(context).doesNotHaveBean(HashRedisOperations.class);
            assertThat(context).doesNotHaveBean(SetRedisOperations.class);
            assertThat(context).doesNotHaveBean(RedisLockExecutor.class);
        });
    }

    @Test
    void allowsOperationsAndLockFeaturesToBeDisabledIndependently() {
        this.contextRunner
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .withPropertyValues("wuli3.redis.operations.enabled=false", "wuli3.redis.lock.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RedisSupport.class);
                    assertThat(context).doesNotHaveBean(RedisCodec.class);
                    assertThat(context).doesNotHaveBean(StringRedisOperations.class);
                    assertThat(context).doesNotHaveBean(ObjectRedisOperations.class);
                    assertThat(context).doesNotHaveBean(HashRedisOperations.class);
                    assertThat(context).doesNotHaveBean(SetRedisOperations.class);
                    assertThat(context).doesNotHaveBean(RedisLockExecutor.class);
                    final RedisProperties properties = context.getBean(RedisProperties.class);
                    assertThat(properties.getOperations().isEnabled()).isFalse();
                    assertThat(properties.getLock().isEnabled()).isFalse();
                });
    }

    @Test
    void backsOffForApplicationProvidedBeans() {
        final RedisSupport redisSupport = mock(RedisSupport.class);
        final RedisLockExecutor lockExecutor = mock(RedisLockExecutor.class);
        this.contextRunner
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .withBean(RedisSupport.class, () -> redisSupport)
                .withBean(RedisLockExecutor.class, () -> lockExecutor)
                .run(context -> {
                    assertThat(context).getBean(RedisSupport.class).isSameAs(redisSupport);
                    assertThat(context).getBean(RedisLockExecutor.class).isSameAs(lockExecutor);
                });
    }

    @Test
    void usesApplicationProvidedCodec() {
        final RedisCodec codec = mock(RedisCodec.class);
        when(codec.keySerializer(any(RedisKeyPrefix.class))).thenReturn(StringRedisSerializer.UTF_8);
        when(codec.hashKeySerializer()).thenReturn(StringRedisSerializer.UTF_8);
        this.contextRunner
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withBean(RedisCodec.class, () -> codec)
                .run(context -> {
                    assertThat(context.getBean(RedisCodec.class)).isSameAs(codec);
                    assertThat(context.getBean(RedisSupport.class).codec()).isSameAs(codec);
                });
    }

    @Test
    void ignoresApplicationTemplateSerializersAndAppliesTheServicePrefix() {
        final StringRedisTemplate applicationTemplate = new StringRedisTemplate(mock(RedisConnectionFactory.class));
        applicationTemplate.setValueSerializer(new StringRedisSerializer(StandardCharsets.UTF_16));
        final RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        this.contextRunner
                .withBean(StringRedisTemplate.class, () -> applicationTemplate)
                .withBean(RedisConnectionFactory.class, () -> connectionFactory)
                .run(context -> {
                    final RedisTemplate<String, String> template =
                            context.getBean(RedisSupport.class).redisTemplate();

                    assertThat(template).isNotSameAs(applicationTemplate);
                    assertThat(template.getConnectionFactory()).isSameAs(connectionFactory);
                    assertThat(template.getValueSerializer()).isSameAs(StringRedisSerializer.UTF_8);
                    assertThat(RedisAutoConfigurationTest.keySerializer(template)
                                    .serialize("orders:1"))
                            .isEqualTo("order-service:orders:1".getBytes(StandardCharsets.UTF_8));
                    assertThat(RedisAutoConfigurationTest.keySerializer(applicationTemplate)
                                    .serialize("orders:1"))
                            .isEqualTo("orders:1".getBytes(StandardCharsets.UTF_8));
                });
    }

    @Test
    void serviceCodeOverridesApplicationNameAndWorksWithoutIt() {
        for (final String applicationName : new String[] {"order-service", ""}) {
            this.contextRunner
                    .withPropertyValues(
                            "application.service.service-code=shared-service",
                            "spring.application.name=" + applicationName)
                    .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(RedisAutoConfigurationTest.keySerializer(context.getBean(RedisSupport.class)
                                                .redisTemplate())
                                        .serialize("orders:1"))
                                .isEqualTo("shared-service:orders:1".getBytes(StandardCharsets.UTF_8));
                    });
        }
    }

    @Test
    void blankServiceCodeUsesApplicationNameForKeys() {
        this.contextRunner
                .withPropertyValues("application.service.service-code=   ")
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .run(context -> assertThat(RedisAutoConfigurationTest.keySerializer(
                                        context.getBean(RedisSupport.class).redisTemplate())
                                .serialize("orders:1"))
                        .isEqualTo("order-service:orders:1".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void failsWithoutAnApplicationName() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(CoreSpringBootAutoConfiguration.class, RedisAutoConfiguration.class))
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .run(context ->
                        assertThat(context).hasFailed().getFailure().hasMessageContaining("spring.application.name"));
    }

    @SuppressWarnings("unchecked")
    private static RedisSerializer<String> keySerializer(final RedisTemplate<String, String> template) {
        return (RedisSerializer<String>) Objects.requireNonNull(template.getKeySerializer(), "keySerializer");
    }
}
