package com.kjs.wuli3.it.order.infra.event;

import com.kjs.wuli3.event.EventPublisher;
import com.kjs.wuli3.event.envelope.EventEnvelope;
import com.kjs.wuli3.it.order.app.port.out.OrderEvents;
import com.kjs.wuli3.it.order.domain.OrderCreated;
import com.kjs.wuli3.rocket.v5.RocketV5PublishOptions;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 在数据库提交后同步发送订单事件，用于验收 v5 传输与事务同步。
 *
 * 注意：提交后发送失败不能撤销已经提交的订单，不提供 Outbox 恢复能力。
 *
 * @author 国杨 create on 2026/9/30 10:00
 */
@Component
public final class RocketOrderEvents implements OrderEvents {
    private static final RocketV5PublishOptions OPTIONS =
            RocketV5PublishOptions.builder().afterCommit(true).build();
    private final EventPublisher publisher;
    private final String topic;

    /** 注入统一事件发布入口和验收主题。 */
    public RocketOrderEvents(
            final EventPublisher publisher, @Value("${it.order.topic:order_it_events}") final String topic) {
        this.publisher = publisher;
        this.topic = topic;
    }

    /** 注册带唯一事件 ID 的订单创建事件。 */
    @Override
    public void created(final OrderCreated event) {
        this.publisher.publish(
                RocketOrderEvents.OPTIONS,
                new EventEnvelope<>(
                        this.topic, "order.created.v1", UUID.randomUUID().toString(), Instant.now(), event));
    }
}
