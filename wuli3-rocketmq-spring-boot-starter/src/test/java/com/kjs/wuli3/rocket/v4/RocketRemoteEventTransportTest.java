package com.kjs.wuli3.rocket.v4;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.event.envelope.EventEnvelope;
import com.kjs.wuli3.event.error.SendFailedException;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.rocket.message.RocketMessageWrapperEncoder;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;

class RocketRemoteEventTransportTest {

    /** 验证普通与顺序异步发送转发业务回调，保留 SDK 原生结果。 */
    @Test
    void notifiesSynchronousResultAndIsolatesCallbackFailures() {
        final RocketMQTemplate template = mock(RocketMQTemplate.class);
        final RocketRemoteEventTransport transport = RocketRemoteEventTransportTest.transport(template);
        final SendCallback callback = mock(SendCallback.class);
        final org.apache.rocketmq.client.producer.SendResult result =
                mock(org.apache.rocketmq.client.producer.SendResult.class);
        org.mockito.Mockito.when(template.syncSend(eq("orders"), any(Message.class)))
                .thenReturn(result);
        org.mockito.Mockito.doThrow(new IllegalStateException("callback failed"))
                .when(callback)
                .onSuccess(result);
        transport.send(
                RocketV4PublishOptions.builder().sendCallback(callback).build(),
                RocketRemoteEventTransportTest.envelope());
        verify(callback).onSuccess(result);
        org.mockito.Mockito.verifyNoMoreInteractions(callback);
    }

    @Test
    void preservesSynchronousFailureWhenFailureCallbackThrows() {
        final RocketMQTemplate template = mock(RocketMQTemplate.class);
        final RuntimeException failure = new IllegalStateException("send failed");
        final SendCallback callback = mock(SendCallback.class);
        org.mockito.Mockito.doThrow(failure).when(template).syncSend(eq("orders"), any(Message.class));
        org.mockito.Mockito.doThrow(new IllegalStateException("callback failed"))
                .when(callback)
                .onException(failure);
        final RocketRemoteEventTransport transport = RocketRemoteEventTransportTest.transport(template);
        assertThatThrownBy(() -> transport.send(
                        RocketV4PublishOptions.builder().sendCallback(callback).build(),
                        RocketRemoteEventTransportTest.envelope()))
                .isInstanceOf(SendFailedException.class)
                .hasCause(failure);
        verify(callback).onException(failure);
    }

    @Test
    void forwardsBusinessCallbackForBothAsyncModes() {
        final RocketMQTemplate template = mock(RocketMQTemplate.class);
        final RocketRemoteEventTransport transport = RocketRemoteEventTransportTest.transport(template);
        final SendCallback callback = mock(SendCallback.class);
        final org.apache.rocketmq.client.producer.SendResult result =
                mock(org.apache.rocketmq.client.producer.SendResult.class);
        org.mockito.Mockito.doAnswer(invocation -> {
                    invocation.<SendCallback>getArgument(2).onSuccess(result);
                    return null;
                })
                .when(template)
                .asyncSend(eq("orders"), any(Message.class), any(SendCallback.class));
        final RuntimeException failure = new IllegalStateException("broker unavailable");
        org.mockito.Mockito.doAnswer(invocation -> {
                    invocation.<SendCallback>getArgument(3).onException(failure);
                    return null;
                })
                .when(template)
                .asyncSendOrderly(eq("orders"), any(Message.class), eq("order-42"), any(SendCallback.class));

        transport.send(
                RocketV4PublishOptions.builder()
                        .async(true)
                        .sendCallback(callback)
                        .build(),
                RocketRemoteEventTransportTest.envelope());
        transport.send(
                RocketV4PublishOptions.builder()
                        .async(true)
                        .orderKey("order-42")
                        .sendCallback(callback)
                        .build(),
                RocketRemoteEventTransportTest.envelope());

        verify(callback).onSuccess(result);
        verify(callback).onException(failure);
        org.mockito.Mockito.verifyNoMoreInteractions(callback);
    }

    /** 验证启动失败通过异常返回，不额外通知业务回调。 */
    @Test
    void throwsStartupFailureWithoutCallingCallback() {
        final RocketMQTemplate template = mock(RocketMQTemplate.class);
        final SendCallback callback = mock(SendCallback.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("startup failed"))
                .when(template)
                .asyncSend(eq("orders"), any(Message.class), any(SendCallback.class));
        final RocketRemoteEventTransport transport = RocketRemoteEventTransportTest.transport(template);
        assertThatThrownBy(() -> transport.send(
                        RocketV4PublishOptions.builder()
                                .async(true)
                                .sendCallback(callback)
                                .build(),
                        RocketRemoteEventTransportTest.envelope()))
                .isInstanceOf(SendFailedException.class);
        verifyNoInteractions(callback);
    }

    @Test
    void mapsSupportedPublicationModesToRocketMqTemplate() {
        final RocketMQTemplate template = mock(RocketMQTemplate.class);
        final RocketRemoteEventTransport transport = RocketRemoteEventTransportTest.transport(template);
        final EventEnvelope<String> envelope = RocketRemoteEventTransportTest.envelope();

        transport.send(RocketRemoteEventTransportTest.options(), envelope);
        transport.send(RocketV4PublishOptions.builder().async(true).build(), envelope);
        transport.send(RocketV4PublishOptions.builder().orderKey("order-42").build(), envelope);
        transport.send(
                RocketV4PublishOptions.builder()
                        .orderKey("order-42")
                        .async(true)
                        .build(),
                envelope);
        transport.send(
                RocketV4PublishOptions.builder().delay(Duration.ofSeconds(5)).build(), envelope);

        verify(template).syncSend(eq("orders"), any(Message.class));
        verify(template).asyncSend(eq("orders"), any(Message.class), any(SendCallback.class));
        verify(template).syncSendOrderly(eq("orders"), any(Message.class), eq("order-42"));
        verify(template).asyncSendOrderly(eq("orders"), any(Message.class), eq("order-42"), any(SendCallback.class));
        verify(template).syncSendDelayTimeMills(eq("orders"), any(Message.class), eq(5000L));
    }

    @Test
    void sendsEachEnvelope() {
        final RocketMQTemplate template = mock(RocketMQTemplate.class);
        final RocketRemoteEventTransport transport = RocketRemoteEventTransportTest.transport(template);

        transport.send(
                RocketRemoteEventTransportTest.options(),
                RocketRemoteEventTransportTest.envelope(),
                RocketRemoteEventTransportTest.envelope());

        verify(template, org.mockito.Mockito.times(2)).syncSend(eq("orders"), any(Message.class));
    }

    @Test
    void wrapsTemplateFailuresAndRejectsInvalidOptionsBeforeTemplateInteraction() {
        final RocketMQTemplate failingTemplate = mock(RocketMQTemplate.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("unavailable"))
                .when(failingTemplate)
                .syncSend(eq("orders"), any(Message.class));
        final RocketRemoteEventTransport failingTransport = RocketRemoteEventTransportTest.transport(failingTemplate);

        assertThatThrownBy(() -> failingTransport.send(
                        RocketRemoteEventTransportTest.options(), RocketRemoteEventTransportTest.envelope()))
                .isInstanceOf(SendFailedException.class)
                .hasCauseInstanceOf(IllegalStateException.class);

        final RocketMQTemplate untouchedTemplate = mock(RocketMQTemplate.class);
        final RocketRemoteEventTransport untouchedTransport =
                RocketRemoteEventTransportTest.transport(untouchedTemplate);
        final EventEnvelope<String> invalidEnvelope =
                new EventEnvelope<>("invalid topic", "order.paid.v1", "event-1", Instant.EPOCH, "payload");
        assertThatThrownBy(() -> untouchedTransport.send(RocketRemoteEventTransportTest.options(), invalidEnvelope))
                .isInstanceOf(ErrorCodeException.class);
        verifyNoInteractions(untouchedTemplate);
    }

    private static RocketRemoteEventTransport transport(final RocketMQTemplate template) {
        return new RocketRemoteEventTransport(
                template, new RocketMessageWrapperEncoder(null, new ContextPropagator(List.of())));
    }

    private static EventEnvelope<String> envelope() {
        return new EventEnvelope<>("orders", "order.paid.v1", "event-1", Instant.EPOCH, "payload");
    }

    private static RocketV4PublishOptions options() {
        return new RocketV4PublishOptions();
    }
}
