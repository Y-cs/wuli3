package com.kjs.wuli3.rocket.v5.autoconfigure;

import com.kjs.wuli3.event.autoconfigure.ConditionalOnMissingRoutingEventTransport;
import com.kjs.wuli3.event.autoconfigure.EventAutoConfiguration;
import com.kjs.wuli3.rocket.autoconfigure.RocketCommonAutoConfiguration;
import com.kjs.wuli3.rocket.message.RocketMessageWrapperEncoder;
import com.kjs.wuli3.rocket.transaction.RocketLocalTransactionRegistry;
import com.kjs.wuli3.rocket.v5.RocketV5PublishOptions;
import com.kjs.wuli3.rocket.v5.RocketV5RemoteEventTransport;
import com.kjs.wuli3.rocket.v5.RocketV5TransactionChecker;
import java.time.Clock;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.SessionCredentials;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 通过 application.yml 配置或业务自定义 Producer 注册 v5 传输。
 *
 * @author GuoYang create on 2026/9/30 10:00
 */
@AutoConfiguration(after = RocketCommonAutoConfiguration.class, before = EventAutoConfiguration.class)
@EnableConfigurationProperties(RocketV5Properties.class)
@ConditionalOnClass(
        name = {
            "org.apache.rocketmq.client.apis.ClientServiceProvider",
            "org.apache.rocketmq.client.apis.producer.Producer"
        })
public class RocketV5AutoConfiguration {

    /** 提供可覆盖的 SDK 工厂，不创建网络客户端。 */
    @Bean
    @ConditionalOnMissingBean(ClientServiceProvider.class)
    ClientServiceProvider rocketV5ClientServiceProvider() {
        return ClientServiceProvider.loadService();
    }

    /** 创建事务回查分发器；业务自定义 Producer 时需显式装配。 */
    @Bean
    @ConditionalOnMissingBean
    RocketV5TransactionChecker rocketV5TransactionChecker(final RocketLocalTransactionRegistry transactionRegistry) {
        return new RocketV5TransactionChecker(transactionRegistry);
    }

    /** 使用配置创建默认 Producer；业务提供同类型 Bean 时自动让位。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(Producer.class)
    @ConditionalOnBean(ClientServiceProvider.class)
    @ConditionalOnProperty(prefix = "wuli3.rocketmq.v5", name = "endpoints")
    Producer rocketV5Producer(
            final ClientServiceProvider provider,
            final RocketV5Properties properties,
            final RocketV5TransactionChecker transactionChecker)
            throws Exception {
        if (properties.getTopics().isEmpty()) {
            throw new IllegalStateException("wuli3.rocketmq.v5.topics must contain at least one topic");
        }
        final String endpoints = java.util.Objects.requireNonNull(properties.getEndpoints());
        final var builder = ClientConfiguration.newBuilder()
                .setEndpoints(endpoints)
                .setRequestTimeout(properties.getRequestTimeout())
                .enableSsl(properties.isSslEnabled())
                .setMaxStartupAttempts(properties.getMaxStartupAttempts());
        if (properties.getNamespace() != null && !properties.getNamespace().isBlank()) {
            builder.setNamespace(properties.getNamespace());
        }
        if (properties.getAccessKey() != null && properties.getAccessSecret() != null) {
            final SessionCredentials credentials = properties.getSecurityToken() == null
                    ? new SessionCredentials(properties.getAccessKey(), properties.getAccessSecret())
                    : new SessionCredentials(
                            properties.getAccessKey(), properties.getAccessSecret(), properties.getSecurityToken());
            builder.setCredentialProvider(() -> credentials);
        }
        final ClientConfiguration configuration = builder.build();
        return provider.newProducerBuilder()
                .setClientConfiguration(configuration)
                .setTopics(properties.getTopics().toArray(String[]::new))
                .setMaxAttempts(properties.getMaxAttempts())
                .setTransactionChecker(transactionChecker)
                .build();
    }

    /** 创建 v5 传输，业务可按 v5 选项类型覆盖。 */
    @Bean
    @ConditionalOnBean(Producer.class)
    @ConditionalOnMissingRoutingEventTransport(optionsType = RocketV5PublishOptions.class)
    RocketV5RemoteEventTransport rocketV5RemoteEventTransport(
            final Producer producer,
            final ClientServiceProvider provider,
            final RocketMessageWrapperEncoder encoder,
            final RocketLocalTransactionRegistry transactionRegistry) {
        return new RocketV5RemoteEventTransport(producer, provider, encoder, Clock.systemUTC(), transactionRegistry);
    }
}
