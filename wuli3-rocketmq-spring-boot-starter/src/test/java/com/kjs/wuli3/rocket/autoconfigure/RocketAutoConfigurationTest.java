package com.kjs.wuli3.rocket.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.kjs.wuli3.event.PublishOptions;
import com.kjs.wuli3.event.remote.RemoteEventTransport;
import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.propagation.store.ThreadLocalContextBackend;
import com.kjs.wuli3.rocket.RocketContextSupport;
import com.kjs.wuli3.rocket.message.RocketMessageWrapperEncoder;
import com.kjs.wuli3.rocket.v4.RocketRemoteEventTransport;
import com.kjs.wuli3.rocket.v4.RocketV4PublishOptions;
import com.kjs.wuli3.rocket.v4.autoconfigure.RocketV4AutoConfiguration;
import com.kjs.wuli3.rocket.v5.RocketV5RemoteEventTransport;
import com.kjs.wuli3.rocket.v5.autoconfigure.RocketV5AutoConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.ProducerBuilder;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RocketAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RocketCommonAutoConfiguration.class,
                    RocketV4AutoConfiguration.class,
                    RocketV5AutoConfiguration.class));

    /** 验证两种选项可以通过同一个发布器分别路由。 */
    @Test
    void routesBothVersionsThroughPublisher() {
        final RocketMQTemplate template = mock(RocketMQTemplate.class);
        final Producer producer = mock(Producer.class);
        this.contextRunner
                .withConfiguration(
                        AutoConfigurations.of(com.kjs.wuli3.event.autoconfigure.EventAutoConfiguration.class))
                .withBean(RocketMQTemplate.class, () -> template)
                .withBean(Producer.class, () -> producer)
                .run(context -> {
                    final com.kjs.wuli3.event.EventPublisher publisher =
                            context.getBean(com.kjs.wuli3.event.EventPublisher.class);
                    final com.kjs.wuli3.event.envelope.EventEnvelope<String> event =
                            new com.kjs.wuli3.event.envelope.EventEnvelope<>(
                                    "orders", "created", "id-1", java.time.Instant.EPOCH, "payload");
                    publisher.publish(new RocketV4PublishOptions(), event);
                    publisher.publish(new com.kjs.wuli3.rocket.v5.RocketV5PublishOptions(), event);
                    org.mockito.Mockito.verify(template)
                            .syncSend(
                                    org.mockito.ArgumentMatchers.eq("orders"),
                                    org.mockito.ArgumentMatchers.any(org.springframework.messaging.Message.class));
                    try {
                        org.mockito.Mockito.verify(producer)
                                .send(org.mockito.ArgumentMatchers.any(
                                        org.apache.rocketmq.client.apis.message.Message.class));
                    } catch (final org.apache.rocketmq.client.apis.ClientException exception) {
                        throw new AssertionError(exception);
                    }
                });
    }

    /** 覆盖 v4 不应屏蔽 v5 默认传输。 */
    @Test
    void overridingV4KeepsV5Registered() {
        this.contextRunner
                .withBean(RocketMQTemplate.class, () -> mock(RocketMQTemplate.class))
                .withBean(Producer.class, () -> mock(Producer.class))
                .withBean(NoopRocketTransport.class, NoopRocketTransport::new)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RocketRemoteEventTransport.class);
                    assertThat(context).hasSingleBean(RocketV5RemoteEventTransport.class);
                });
    }

    @Test
    void registersDefaultEncoderAndTransportWhenTemplateIsAvailable() {
        this.contextRunner
                .withBean(RocketMQTemplate.class, () -> mock(RocketMQTemplate.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(RocketMessageWrapperEncoder.class);
                    assertThat(context).hasSingleBean(RemoteEventTransport.class);
                    assertThat(context).hasSingleBean(RocketRemoteEventTransport.class);
                    assertThat(context).doesNotHaveBean(RocketContextSupport.class);
                });
    }

    @Test
    void createsV5ProducerFromConfigurationWhenApplicationDoesNotProvideOne()
            throws org.apache.rocketmq.client.apis.ClientException {
        final ClientServiceProvider provider = mock(ClientServiceProvider.class);
        final ProducerBuilder builder = mock(ProducerBuilder.class);
        final Producer producer = mock(Producer.class);
        org.mockito.Mockito.when(provider.newProducerBuilder()).thenReturn(builder);
        org.mockito.Mockito.when(builder.setClientConfiguration(org.mockito.ArgumentMatchers.any()))
                .thenReturn(builder);
        org.mockito.Mockito.when(builder.setTopics(org.mockito.ArgumentMatchers.any(String[].class)))
                .thenReturn(builder);
        org.mockito.Mockito.when(builder.setMaxAttempts(org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(builder);
        org.mockito.Mockito.when(builder.build()).thenReturn(producer);
        this.contextRunner
                .withPropertyValues(
                        "wuli3.rocketmq.v5.endpoints=localhost:8081",
                        "wuli3.rocketmq.v5.topics[0]=orders",
                        "wuli3.rocketmq.v5.request-timeout=5s")
                .withBean(ClientServiceProvider.class, () -> provider)
                .run(context -> {
                    assertThat(context).hasSingleBean(Producer.class);
                    org.mockito.Mockito.verify(builder).setTopics("orders");
                });
    }

    @Test
    void registersInboundContextSupportWhenAWriterIsAvailable() {
        this.contextRunner
                .withBean(RocketMQTemplate.class, () -> mock(RocketMQTemplate.class))
                .withBean(ThreadLocalContextBackend.class, ThreadLocalContextBackend::new)
                .withBean(ContextManager.class, () -> {
                    final ThreadLocalContextBackend backend = new ThreadLocalContextBackend();
                    return new ContextManager(backend, backend);
                })
                .run(context -> {
                    assertThat(context).hasSingleBean(RocketContextSupport.class);
                });
    }

    @Test
    void backsOffForApplicationProvidedBeans() {
        this.contextRunner
                .withBean(RocketMQTemplate.class, () -> mock(RocketMQTemplate.class))
                .withBean(RemoteEventTransport.class, NoopRocketTransport::new)
                .withBean(
                        RocketMessageWrapperEncoder.class,
                        () -> new RocketMessageWrapperEncoder(null, new ContextPropagator(java.util.List.of())))
                .run(context -> {
                    assertThat(context).hasSingleBean(RemoteEventTransport.class);
                    assertThat(context).doesNotHaveBean(RocketRemoteEventTransport.class);
                    assertThat(context).hasSingleBean(RocketMessageWrapperEncoder.class);
                });
    }

    @Test
    void coexistsWithARemoteTransportForAnotherOptionsType() {
        this.contextRunner
                .withBean(RocketMQTemplate.class, () -> mock(RocketMQTemplate.class))
                .withBean(OtherRemoteTransport.class, OtherRemoteTransport::new)
                .run(context -> {
                    assertThat(context).getBeans(RemoteEventTransport.class).hasSize(2);
                    assertThat(context).hasSingleBean(RocketRemoteEventTransport.class);
                });
    }

    @Test
    void registersBothTransportsWhenBothClientsExist() {
        this.contextRunner
                .withBean(RocketMQTemplate.class, () -> mock(RocketMQTemplate.class))
                .withBean(Producer.class, () -> mock(Producer.class))
                .withBean(ClientServiceProvider.class, () -> mock(ClientServiceProvider.class))
                .run(context -> {
                    assertThat(context).getBeans(RemoteEventTransport.class).hasSize(2);
                    assertThat(context).hasSingleBean(RocketV5RemoteEventTransport.class);
                    assertThat(context).hasSingleBean(RocketRemoteEventTransport.class);
                });
    }

    @Test
    void skipsV5WithoutAnApplicationProducer() {
        this.contextRunner
                .withBean(ClientServiceProvider.class, () -> mock(ClientServiceProvider.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(RocketV5RemoteEventTransport.class);
                });
    }

    @Test
    void registersV5TransportWithoutV4Template() {
        this.contextRunner.withBean(Producer.class, () -> mock(Producer.class)).run(context -> {
            assertThat(context).hasSingleBean(ClientServiceProvider.class);
            assertThat(context).hasSingleBean(RemoteEventTransport.class);
            assertThat(context).hasSingleBean(RocketV5RemoteEventTransport.class);
        });
    }

    @Test
    void keepsV4TransportUsableWithoutTheOptionalV5Client() {
        this.contextRunner
                .withClassLoader(new FilteredClassLoader("org.apache.rocketmq.client.apis"))
                .withBean(RocketMQTemplate.class, () -> mock(RocketMQTemplate.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(RemoteEventTransport.class);
                    assertThat(context).hasSingleBean(RocketRemoteEventTransport.class);
                });
    }

    private static final class NoopRocketTransport implements RemoteEventTransport<RocketV4PublishOptions> {

        @Override
        public Class<RocketV4PublishOptions> supportedOptionsType() {
            return RocketV4PublishOptions.class;
        }

        @Override
        public void send(
                final RocketV4PublishOptions options,
                final com.kjs.wuli3.event.envelope.EventEnvelope<?>... envelopes) {}
    }

    private record OtherOptions() implements PublishOptions {}

    private static final class OtherRemoteTransport implements RemoteEventTransport<OtherOptions> {

        @Override
        public Class<OtherOptions> supportedOptionsType() {
            return OtherOptions.class;
        }

        @Override
        public void send(
                final OtherOptions options, final com.kjs.wuli3.event.envelope.EventEnvelope<?>... envelopes) {}
    }
}
