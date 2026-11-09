package com.kjs.wuli3.rocket.v5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kjs.wuli3.event.envelope.EventEnvelope;
import com.kjs.wuli3.json.core.Jsons;
import com.kjs.wuli3.rocket.transaction.RocketLocalTransaction;
import com.kjs.wuli3.rocket.transaction.RocketLocalTransactionRegistry;
import com.kjs.wuli3.rocket.transaction.RocketLocalTransactionState;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.apis.producer.TransactionResolution;
import org.junit.jupiter.api.Test;

class RocketV5TransactionCheckerTest {

    /** 验证回查按事件类型分发并映射事务结论。 */
    @Test
    void mapsCheckResultByEventType() {
        final RocketLocalTransaction transaction = mock(RocketLocalTransaction.class);
        when(transaction.eventType()).thenReturn("order.paid.v1");
        when(transaction.check(org.mockito.ArgumentMatchers.any())).thenReturn(RocketLocalTransactionState.ROLLBACK);
        final RocketV5TransactionChecker checker =
                new RocketV5TransactionChecker(new RocketLocalTransactionRegistry(List.of(transaction)));

        assertThat(checker.check(RocketV5TransactionCheckerTest.view("order.paid.v1")))
                .isEqualTo(TransactionResolution.ROLLBACK);
        assertThat(checker.check(RocketV5TransactionCheckerTest.view("order.cancelled.v1")))
                .isEqualTo(TransactionResolution.UNKNOWN);
    }

    /** 验证无法解析的消息体返回 UNKNOWN。 */
    @Test
    void returnsUnknownForMalformedBody() {
        final RocketV5TransactionChecker checker =
                new RocketV5TransactionChecker(new RocketLocalTransactionRegistry(List.of()));
        final MessageView view = mock(MessageView.class);
        when(view.getBody()).thenReturn(ByteBuffer.wrap("not-json".getBytes(StandardCharsets.UTF_8)));

        assertThat(checker.check(view)).isEqualTo(TransactionResolution.UNKNOWN);
    }

    private static MessageView view(final String eventType) {
        final MessageView view = mock(MessageView.class);
        when(view.getBody())
                .thenReturn(ByteBuffer.wrap(Jsons.toJsonBytes(
                        new EventEnvelope<>("orders", eventType, "event-1", Instant.EPOCH, "payload"))));
        return view;
    }
}
