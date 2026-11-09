package com.kjs.wuli3.rocket.v4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kjs.wuli3.event.envelope.EventEnvelope;
import com.kjs.wuli3.json.core.Jsons;
import com.kjs.wuli3.rocket.transaction.RocketLocalTransaction;
import com.kjs.wuli3.rocket.transaction.RocketLocalTransactionRegistry;
import com.kjs.wuli3.rocket.transaction.RocketLocalTransactionState;
import com.kjs.wuli3.rocket.transaction.RocketTransactionEvent;
import java.time.Instant;
import java.util.List;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

class RocketV4TransactionListenerTest {

    /** 验证执行与回查按事件类型分发并传递线协议内容。 */
    @Test
    void dispatchesExecutionAndCheckByEventType() {
        final RocketLocalTransaction transaction = mock(RocketLocalTransaction.class);
        when(transaction.eventType()).thenReturn("order.paid.v1");
        when(transaction.execute(any())).thenReturn(RocketLocalTransactionState.COMMIT);
        when(transaction.check(any())).thenReturn(RocketLocalTransactionState.ROLLBACK);
        final RocketV4TransactionListener listener =
                new RocketV4TransactionListener(new RocketLocalTransactionRegistry(List.of(transaction)));
        final Message<byte[]> message = MessageBuilder.withPayload(
                        Jsons.toJsonBytes(RocketV4TransactionListenerTest.envelope()))
                .build();

        assertThat(listener.executeLocalTransaction(message, null)).isEqualTo(RocketMQLocalTransactionState.COMMIT);
        assertThat(listener.checkLocalTransaction(message)).isEqualTo(RocketMQLocalTransactionState.ROLLBACK);

        final ArgumentCaptor<RocketTransactionEvent> captor = ArgumentCaptor.forClass(RocketTransactionEvent.class);
        verify(transaction).execute(captor.capture());
        assertThat(captor.getValue().topic()).isEqualTo("orders");
        assertThat(captor.getValue().eventId()).isEqualTo("event-1");
        assertThat(captor.getValue().payloadJson()).isEqualTo("\"payload\"");
    }

    /** 验证未注册事件类型和策略异常都转为 UNKNOWN，交由 broker 回查。 */
    @Test
    void treatsUnregisteredAndFailingTransactionsAsUnknown() {
        final RocketLocalTransaction transaction = mock(RocketLocalTransaction.class);
        when(transaction.eventType()).thenReturn("order.paid.v1");
        when(transaction.execute(any())).thenThrow(new IllegalStateException("database unavailable"));
        final RocketV4TransactionListener listener =
                new RocketV4TransactionListener(new RocketLocalTransactionRegistry(List.of(transaction)));
        final Message<byte[]> paid = MessageBuilder.withPayload(
                        Jsons.toJsonBytes(RocketV4TransactionListenerTest.envelope()))
                .build();
        final Message<byte[]> unknown = MessageBuilder.withPayload(Jsons.toJsonBytes(
                        new EventEnvelope<>("orders", "order.cancelled.v1", "event-2", Instant.EPOCH, "payload")))
                .build();

        assertThat(listener.executeLocalTransaction(paid, null)).isEqualTo(RocketMQLocalTransactionState.UNKNOWN);
        assertThat(listener.executeLocalTransaction(unknown, null)).isEqualTo(RocketMQLocalTransactionState.UNKNOWN);
        assertThat(listener.checkLocalTransaction(
                        MessageBuilder.withPayload("not-json").build()))
                .isEqualTo(RocketMQLocalTransactionState.UNKNOWN);
    }

    private static EventEnvelope<String> envelope() {
        return new EventEnvelope<>("orders", "order.paid.v1", "event-1", Instant.EPOCH, "payload");
    }
}
