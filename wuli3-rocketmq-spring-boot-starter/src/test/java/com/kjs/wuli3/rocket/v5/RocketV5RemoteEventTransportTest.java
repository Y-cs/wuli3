package com.kjs.wuli3.rocket.v5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kjs.wuli3.event.envelope.EventEnvelope;
import com.kjs.wuli3.event.error.SendFailedException;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.rocket.internal.wrapper.RocketMessageWrapperEncoder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.message.MessageBuilder;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.junit.jupiter.api.Test;

class RocketV5RemoteEventTransportTest {

    @Test
    void constructionDoesNotCreateOrProbeAClient() {
        final Producer producer = mock(Producer.class);
        final ClientServiceProvider clientServiceProvider = mock(ClientServiceProvider.class);

        RocketV5RemoteEventTransportTest.transport(producer, clientServiceProvider);

        verifyNoInteractions(producer, clientServiceProvider);
    }

    @Test
    void mapsSynchronousAndAsynchronousSendsToTheInjectedProducer() throws Exception {
        final Producer producer = mock(Producer.class);
        final ClientServiceProvider clientServiceProvider = mock(ClientServiceProvider.class);
        final MessageBuilder builder = RocketV5RemoteEventTransportTest.builder(clientServiceProvider);
        final Message message = mock(Message.class);
        when(builder.build()).thenReturn(message);
        when(producer.sendAsync(message)).thenReturn(CompletableFuture.completedFuture(mock(SendReceipt.class)));
        final RocketV5RemoteEventTransport transport =
                RocketV5RemoteEventTransportTest.transport(producer, clientServiceProvider);

        transport.send(RocketV5RemoteEventTransportTest.options(), RocketV5RemoteEventTransportTest.envelope());
        transport.send(
                RocketV5PublishOptions.builder().async(true).build(), RocketV5RemoteEventTransportTest.envelope());

        verify(producer).send(message);
        verify(producer).sendAsync(message);
    }

    @Test
    void mapsOrderingAndExactDelayOnTheJavaClientMessageBuilder() {
        final Producer producer = mock(Producer.class);
        final ClientServiceProvider clientServiceProvider = mock(ClientServiceProvider.class);
        final MessageBuilder builder = RocketV5RemoteEventTransportTest.builder(clientServiceProvider);
        when(builder.build()).thenReturn(mock(Message.class));
        final Clock clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC);
        final RocketV5RemoteEventTransport transport = new RocketV5RemoteEventTransport(
                producer,
                clientServiceProvider,
                new RocketMessageWrapperEncoder(null, new ContextPropagator(List.of())),
                clock);

        transport.send(
                RocketV5PublishOptions.builder().orderKey("order-42").build(),
                RocketV5RemoteEventTransportTest.envelope());
        transport.send(
                RocketV5PublishOptions.builder().delay(Duration.ofSeconds(5)).build(),
                RocketV5RemoteEventTransportTest.envelope());

        verify(builder).setMessageGroup("order-42");
        verify(builder).setDeliveryTimestamp(6_000L);
        verify(builder, times(2)).setTopic("orders");
        verify(builder, times(2)).setKeys("event-1");
    }

    @Test
    void rejectsAsyncFifoAndWrapsAsyncStartupFailures() throws Exception {
        final Producer producer = mock(Producer.class);
        final ClientServiceProvider clientServiceProvider = mock(ClientServiceProvider.class);
        final MessageBuilder builder = RocketV5RemoteEventTransportTest.builder(clientServiceProvider);
        final Message message = mock(Message.class);
        when(builder.build()).thenReturn(message);
        final RocketV5RemoteEventTransport transport =
                RocketV5RemoteEventTransportTest.transport(producer, clientServiceProvider);

        assertThatThrownBy(() -> transport.send(
                        RocketV5PublishOptions.builder()
                                .orderKey("order-42")
                                .async(true)
                                .build(),
                        RocketV5RemoteEventTransportTest.envelope()))
                .hasMessageContaining("does not support async FIFO messages");

        when(producer.sendAsync(message)).thenThrow(new IllegalStateException("unavailable"));
        assertThatThrownBy(() -> transport.send(
                        RocketV5PublishOptions.builder().async(true).build(),
                        RocketV5RemoteEventTransportTest.envelope()))
                .isInstanceOf(SendFailedException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void notifiesSynchronousSuccessAndFailureWithoutLettingCallbackErrorsChangeOutcome() throws Exception {
        final Producer producer = mock(Producer.class);
        final ClientServiceProvider provider = mock(ClientServiceProvider.class);
        final MessageBuilder builder = RocketV5RemoteEventTransportTest.builder(provider);
        final Message message = mock(Message.class);
        final SendReceipt receipt = mock(SendReceipt.class);
        final IllegalStateException failure = new IllegalStateException("send failed");
        final List<Object> notifications = new ArrayList<>();
        when(builder.build()).thenReturn(message);
        when(producer.send(message)).thenReturn(receipt).thenThrow(failure);
        final RocketV5RemoteEventTransport transport = RocketV5RemoteEventTransportTest.transport(producer, provider);
        final RocketV5PublishOptions options = RocketV5PublishOptions.builder()
                .sendCallback((result, error) -> {
                    notifications.add(result);
                    notifications.add(error);
                    throw new IllegalStateException("callback failed");
                })
                .build();

        transport.send(options, RocketV5RemoteEventTransportTest.envelope());
        assertThatThrownBy(() -> transport.send(options, RocketV5RemoteEventTransportTest.envelope()))
                .isInstanceOf(SendFailedException.class)
                .hasCause(failure);
        assertThat(notifications).containsExactly(receipt, null, null, failure);
    }

    @Test
    void notifiesAsyncCompletionAndSupportsDelayedPublication() throws Exception {
        final Producer producer = mock(Producer.class);
        final ClientServiceProvider provider = mock(ClientServiceProvider.class);
        final MessageBuilder builder = RocketV5RemoteEventTransportTest.builder(provider);
        final Message message = mock(Message.class);
        final SendReceipt receipt = mock(SendReceipt.class);
        final CompletableFuture<SendReceipt> success = new CompletableFuture<>();
        final CompletableFuture<SendReceipt> failed = new CompletableFuture<>();
        final IllegalStateException failure = new IllegalStateException("send failed");
        final List<Object> notifications = new ArrayList<>();
        when(builder.build()).thenReturn(message);
        when(producer.sendAsync(message)).thenReturn(success).thenReturn(failed);
        final RocketV5RemoteEventTransport transport = RocketV5RemoteEventTransportTest.transport(producer, provider);
        final RocketV5PublishOptions options = RocketV5PublishOptions.builder()
                .async(true)
                .delay(Duration.ofSeconds(1))
                .sendCallback((result, error) -> {
                    notifications.add(result);
                    notifications.add(error);
                    throw new IllegalStateException("callback failed");
                })
                .build();

        transport.send(options, RocketV5RemoteEventTransportTest.envelope());
        transport.send(options, RocketV5RemoteEventTransportTest.envelope());
        assertThat(notifications).isEmpty();
        success.complete(receipt);
        failed.completeExceptionally(failure);
        assertThat(notifications).containsExactly(receipt, null, null, failure);
        verify(builder, times(2)).setDeliveryTimestamp(anyLong());
    }

    @Test
    void doesNotNotifyCallbackWhenAsyncSendCannotStart() throws Exception {
        final Producer producer = mock(Producer.class);
        final ClientServiceProvider provider = mock(ClientServiceProvider.class);
        final MessageBuilder builder = RocketV5RemoteEventTransportTest.builder(provider);
        final Message message = mock(Message.class);
        final List<Object> notifications = new ArrayList<>();
        when(builder.build()).thenReturn(message);
        when(producer.sendAsync(message)).thenThrow(new IllegalStateException("unavailable"));
        final RocketV5RemoteEventTransport transport = RocketV5RemoteEventTransportTest.transport(producer, provider);
        final RocketV5PublishOptions options = RocketV5PublishOptions.builder()
                .async(true)
                .sendCallback((result, error) -> notifications.add(error))
                .build();

        assertThatThrownBy(() -> transport.send(options, RocketV5RemoteEventTransportTest.envelope()))
                .isInstanceOf(SendFailedException.class);
        assertThat(notifications).isEmpty();
    }

    @Test
    void rejectsOrderedDelayBeforeSending() {
        final Producer producer = mock(Producer.class);
        final ClientServiceProvider provider = mock(ClientServiceProvider.class);
        final RocketV5RemoteEventTransport transport = RocketV5RemoteEventTransportTest.transport(producer, provider);

        assertThatThrownBy(() -> transport.send(
                        RocketV5PublishOptions.builder()
                                .orderKey("order-42")
                                .delay(Duration.ofSeconds(1))
                                .build(),
                        RocketV5RemoteEventTransportTest.envelope()))
                .hasMessageContaining("does not support ordered delayed messages");
        verifyNoInteractions(producer, provider);
    }

    @Test
    void validatesOptionsAndKeepsBuiltOptionsIndependentOfBuilderChanges() {
        assertThatThrownBy(() ->
                        RocketV5PublishOptions.builder().delay(Duration.ZERO).build())
                .hasMessageContaining("delay must be positive");
        assertThatThrownBy(() -> RocketV5PublishOptions.builder().orderKey(" ").build())
                .hasMessageContaining("orderKey cannot be blank");
        final RocketV5PublishOptions.Builder builder = RocketV5PublishOptions.builder();
        final RocketV5PublishOptions defaults = builder.build();
        final RocketV5PublishOptions changed =
                builder.async(true).afterCommit(true).build();
        assertThat(defaults).isEqualTo(new RocketV5PublishOptions());
        assertThat(changed.async()).isTrue();
        assertThat(changed.afterCommit()).isTrue();
    }

    private static RocketV5RemoteEventTransport transport(
            final Producer producer, final ClientServiceProvider clientServiceProvider) {
        return new RocketV5RemoteEventTransport(
                producer,
                clientServiceProvider,
                new RocketMessageWrapperEncoder(null, new ContextPropagator(List.of())),
                Clock.systemUTC());
    }

    private static MessageBuilder builder(final ClientServiceProvider clientServiceProvider) {
        final MessageBuilder builder = mock(MessageBuilder.class);
        when(clientServiceProvider.newMessageBuilder()).thenReturn(builder);
        when(builder.setTopic(anyString())).thenReturn(builder);
        when(builder.setBody(any(byte[].class))).thenReturn(builder);
        when(builder.setKeys(anyString())).thenReturn(builder);
        when(builder.setTag(anyString())).thenReturn(builder);
        when(builder.setMessageGroup(anyString())).thenReturn(builder);
        when(builder.setDeliveryTimestamp(anyLong())).thenReturn(builder);
        return builder;
    }

    private static EventEnvelope<String> envelope() {
        return new EventEnvelope<>("orders", "order.paid.v1", "event-1", Instant.EPOCH, "payload");
    }

    private static RocketV5PublishOptions options() {
        return new RocketV5PublishOptions();
    }
}
