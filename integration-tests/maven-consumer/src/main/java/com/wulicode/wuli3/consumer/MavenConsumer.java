package com.wulicode.wuli3.consumer;

import com.wulicode.wuli3.audit.AuditLogRecorder;
import com.wulicode.wuli3.core.id.UuidStringIdGenerator;
import com.wulicode.wuli3.event.EventPublisher;
import com.wulicode.wuli3.event.envelope.EventEnvelope;
import com.wulicode.wuli3.event.envelope.EventEnvelopeTemplate;
import com.wulicode.wuli3.json.provider.JacksonProvider;
import com.wulicode.wuli3.opentelemetry.autoconfigure.Wuli3OpenTelemetryAutoConfiguration;
import com.wulicode.wuli3.opentelemetry.metrics.MetricRecorder;
import com.wulicode.wuli3.opentelemetry.trace.TraceContextAccessor;
import com.wulicode.wuli3.opentelemetry.trace.TraceMdc;
import com.wulicode.wuli3.redis.RedisKey;
import com.wulicode.wuli3.redis.RedisSupport;
import com.wulicode.wuli3.redis.id.RedisMinuteIdGenerator;
import com.wulicode.wuli3.redis.lock.RedisLock;
import com.wulicode.wuli3.redis.lock.RedisLockExecutor;
import com.wulicode.wuli3.redis.operation.HashRedisOperations;
import com.wulicode.wuli3.redis.operation.ObjectRedisOperations;
import com.wulicode.wuli3.redis.operation.SetRedisOperations;
import com.wulicode.wuli3.redis.operation.StringRedisOperations;
import com.wulicode.wuli3.spring.logging.LoggingProperties;
import io.opentelemetry.api.common.Attributes;
import java.time.Duration;
import java.time.Instant;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public final class MavenConsumer {
    private MavenConsumer() {}

    public static String createIdentifier() {
        JacksonProvider.newJsonMapper();
        final EventEnvelope<ConsumerEvent> envelope =
                EventEnvelopeTemplate.of("consumer-events", "consumer.event.v1").wrap(new ConsumerEvent(Instant.now()));
        envelope.eventId();
        EventPublisher.class.getName();
        RabbitTemplate.class.getName();
        LoggingProperties.class.getName();
        AuditLogRecorder.class.getName();
        Wuli3OpenTelemetryAutoConfiguration.class.getName();
        MetricRecorder.class.getName();
        TraceContextAccessor.class.getName();
        Attributes.class.getName();
        TraceMdc.TRACE_ID.length();
        RedisSupport.class.getName();
        RedisMinuteIdGenerator.class.getName();
        StringRedisOperations.class.getName();
        ObjectRedisOperations.class.getName();
        HashRedisOperations.class.getName();
        SetRedisOperations.class.getName();
        RedisLockExecutor.class.getName();
        RedisKey.expiring("consumer:test", Duration.ofMinutes(1));
        RedisLock.watchdog("consumer:lock", Duration.ZERO);
        assertJavaClientPreviewDependencyIsNotPresent();
        return UuidStringIdGenerator.INSTANCE.nextId();
    }

    private static void assertJavaClientPreviewDependencyIsNotPresent() {
        try {
            Class.forName("org.apache.rocketmq.client.apis.ClientServiceProvider");
        } catch (ClassNotFoundException expected) {
            return;
        }
        throw new IllegalStateException("rocketmq-client-java must not be a published runtime dependency");
    }

    private record ConsumerEvent(Instant occurredOn) {}
}
