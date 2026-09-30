package com.kjs.wuli3.rabbit.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.kjs.wuli3.event.envelope.EventEnvelope;
import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.propagation.context.ContextState;
import com.kjs.wuli3.propagation.internal.InvocationContext;
import com.kjs.wuli3.propagation.store.ThreadLocalContextBackend;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class RabbitMessageEncoderTest {

    @Test
    void storesPropagationHeadersOutsideTheSerializedEnvelope() {
        final ThreadLocalContextBackend threadLocalContextStore = new ThreadLocalContextBackend();
        final ContextState state = ContextState.of(new InvocationContext("10.0.0.8", "request-42"));
        final ContextPropagator contextPropagator = new ContextPropagator(ContextPropagator.standardContextEncoder());
        final EventEnvelope<String> envelope =
                new EventEnvelope<>("orders", "order.paid.v1", "event-1", Instant.EPOCH, "payload");

        final Message[] result = new Message[1];
        new ContextManager(threadLocalContextStore, threadLocalContextStore)
                .with(state)
                .run(() -> result[0] =
                        new RabbitMessageEncoder(threadLocalContextStore, contextPropagator).encode(envelope));
        final Message message = result[0];
        final String body = new String(message.getBody(), StandardCharsets.UTF_8);
        final MessageProperties properties = message.getMessageProperties();

        assertThat(properties.getHeaders())
                .containsEntry("X-Request-Id", "request-42")
                .containsEntry("X-Origin-Ip", "10.0.0.8");
        assertThat(properties.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(properties.getMessageId()).isEqualTo("event-1");
        assertThat(properties.getType()).isEqualTo("order.paid.v1");
        assertThat(body).contains("\"topic\":\"orders\"");
        assertThat(body).doesNotContain("headers", "X-Request-Id", "request-42");
    }

    @Test
    void encodesWithoutPropagationHeadersWhenNoReaderIsAvailable() {
        final EventEnvelope<String> envelope =
                new EventEnvelope<>("orders", "order.paid.v1", "event-1", Instant.EPOCH, "payload");

        final Message message = new RabbitMessageEncoder(null, new ContextPropagator(List.of())).encode(envelope);

        assertThat(message.getMessageProperties().getHeaders()).isEmpty();
    }
}
