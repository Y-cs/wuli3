package com.kjs.wuli3.it.order.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kjs.wuli3.it.order.OrderIntegrationTestApplication;
import com.kjs.wuli3.it.order.api.OrderApi;
import com.kjs.wuli3.it.order.app.port.out.OrderCache;
import com.kjs.wuli3.it.order.app.port.out.OrderCreationLock;
import com.kjs.wuli3.it.order.sharedkernel.OrderId;
import com.kjs.wuli3.redis.error.RedisLockAcquisitionException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.SimpleConsumer;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 通过真实 HTTP、MySQL、Redis 与 RocketMQ 验证订单夹具的组合行为。
 *
 * 注意：MQ 不发送断言只证明有界观察窗口，不能证明任意未来时刻都不会投递。
 *
 * @author 国杨 create on 2026/9/30 17:30
 */
@SpringBootTest(
        classes = OrderIntegrationTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(120)
final class OrderServiceIT {
    private final TestRestTemplate http;
    private final OrderApi orders;
    private final OrderCache cache;
    private final OrderCreationLock lock;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;
    private final Environment environment;
    private final List<String> ids = new ArrayList<>();

    @Autowired
    OrderServiceIT(
            final TestRestTemplate http,
            final OrderApi orders,
            final OrderCache cache,
            final OrderCreationLock lock,
            final JdbcTemplate jdbc,
            final StringRedisTemplate redis,
            final PlatformTransactionManager transactionManager,
            final ObjectMapper json,
            final Environment environment) {
        this.http = http;
        this.orders = orders;
        this.cache = cache;
        this.lock = lock;
        this.jdbc = jdbc;
        this.redis = redis;
        this.transactions = new TransactionTemplate(transactionManager);
        this.json = json;
        this.environment = environment;
    }

    /** 仅清理本测试创建的订单与缓存，不使用清库或通配删除。 */
    @AfterEach
    void cleanOwnedData() {
        for (final String id : this.ids) {
            this.jdbc.update("DELETE FROM it_orders WHERE id = ?", id);
            this.redis.delete(this.cacheKey(id));
        }
    }

    /** 验证 HTTP 响应、数据库约束与错误码投影。 */
    @Test
    void createsAndQueriesOrdersThroughHttp() {
        final String id = this.newId();
        final JsonNode created = this.success(this.http.postForEntity("/api/orders", Map.of("id", id), JsonNode.class));
        assertThat(created.path("data").path("id").asText()).isEqualTo(id);
        assertThat(created.path("data").path("status").asText()).isEqualTo("CREATED");
        assertThat(this.jdbc.queryForObject("SELECT status FROM it_orders WHERE id = ?", String.class, id))
                .isEqualTo("CREATED");
        final JsonNode queried = this.success(this.http.getForEntity("/api/orders/{id}", JsonNode.class, id));
        assertThat(queried.path("data")).isEqualTo(created.path("data"));

        final String duplicateCode =
                this.failure(this.http.postForEntity("/api/orders", Map.of("id", id), JsonNode.class));
        final String missingCode =
                this.failure(this.http.getForEntity("/api/orders/{id}", JsonNode.class, this.newId()));
        final String invalidCode =
                this.failure(this.http.postForEntity("/api/orders", Map.of("id", " "), JsonNode.class));
        assertThat(List.of(duplicateCode, missingCode, invalidCode)).doesNotHaveDuplicates();
        assertThat(this.count(id)).isEqualTo(1);
        assertThatThrownBy(() -> this.jdbc.update("INSERT INTO it_orders (id, status) VALUES (?, ?)", id, "CREATED"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    /** 验证真实缓存的回填、对象往返、命中、TTL、过期与删除。 */
    @Test
    void cachesOrdersWithBoundedLifetime() throws InterruptedException {
        final String id = this.newId();
        this.orders.create(id);
        assertThat(this.cache.find(new OrderId(id))).isEmpty();
        assertThat(this.orders.find(id).id()).isEqualTo(id);
        assertThat(this.cache.find(new OrderId(id)))
                .hasValueSatisfying(order -> assertThat(order.id().value()).isEqualTo(id));
        assertThat(this.redis.getExpire(this.cacheKey(id), TimeUnit.SECONDS)).isBetween(1L, 60L);
        // 删除持久化记录后仍可查询，证明查询确实命中缓存。
        this.jdbc.update("DELETE FROM it_orders WHERE id = ?", id);
        assertThat(this.orders.find(id).id()).isEqualTo(id);
        assertThat(this.redis.expire(this.cacheKey(id), Duration.ofMillis(100))).isTrue();
        final long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (Boolean.TRUE.equals(this.redis.hasKey(this.cacheKey(id))) && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertThat(this.cache.find(new OrderId(id))).isEmpty();
        assertThatThrownBy(() -> this.orders.find(id)).isInstanceOf(com.kjs.wuli3.core.error.ErrorCodeException.class);
        assertThat(this.redis.hasKey(this.cacheKey(id))).isFalse();
        final String other = this.newId();
        this.orders.create(other);
        this.orders.find(other);
        assertThat(this.redis.delete(this.cacheKey(other))).isTrue();
        assertThat(this.cache.find(new OrderId(other))).isEmpty();
    }

    /** 持锁期间另一个线程不得进入，正常结束和业务异常均释放锁。 */
    @Test
    void serializesContendersAndReleasesLocksAfterFailure() throws Exception {
        final OrderId id = new OrderId(this.newId());
        final CountDownLatch acquired = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicBoolean contenderEntered = new AtomicBoolean();
        try (final var executor = Executors.newFixedThreadPool(2)) {
            final var holder = executor.submit(() -> this.lock.execute(id, () -> {
                acquired.countDown();
                OrderServiceIT.await(release);
                return "held";
            }));
            try {
                assertThat(acquired.await(10, TimeUnit.SECONDS)).isTrue();
                final var contender = executor.submit(() -> this.lock.execute(id, () -> {
                    contenderEntered.set(true);
                    return "unexpected";
                }));
                assertThatThrownBy(() -> contender.get(10, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(RedisLockAcquisitionException.class);
                assertThat(contenderEntered).isFalse();
            } finally {
                release.countDown();
            }
            assertThat(holder.get(10, TimeUnit.SECONDS)).isEqualTo("held");
            assertThat(executor.submit(() -> this.lock.execute(id, () -> "released"))
                            .get(10, TimeUnit.SECONDS))
                    .isEqualTo("released");
            assertThatThrownBy(() -> this.lock.execute(id, () -> {
                        throw new IllegalStateException("验收业务异常");
                    }))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("验收业务异常");
            // 使用另一线程重新获取，防止可重入锁掩盖当前线程未释放的问题。
            assertThat(executor.submit(() -> this.lock.execute(id, () -> "recovered"))
                            .get(10, TimeUnit.SECONDS))
                    .isEqualTo("recovered");
        }
    }

    /** 先验证消费者可收消息，再验证提交前不发送、提交后发送及回滚不发送。 */
    @Test
    void publishesOnlyAfterDatabaseCommit() throws Exception {
        final String topic = this.environment.getRequiredProperty("it.order.topic");
        final ClientConfiguration configuration = ClientConfiguration.newBuilder()
                .setEndpoints(this.environment.getRequiredProperty("wuli3.rocketmq.v5.endpoints"))
                .enableSsl(false)
                .setRequestTimeout(Duration.ofSeconds(5))
                .build();
        try (final SimpleConsumer consumer = ClientServiceProvider.loadService()
                .newSimpleConsumerBuilder()
                .setClientConfiguration(configuration)
                .setConsumerGroup(this.environment.getProperty("ORDER_IT_MQ_GROUP", "order_it_acceptance"))
                .setSubscriptionExpressions(Map.of(topic, FilterExpression.SUB_ALL))
                .setAwaitDuration(Duration.ofSeconds(1))
                .build()) {
            final String ready = this.newId();
            this.orders.create(ready);
            this.assertReceived(consumer, ready, topic);
            final String committed = this.newId();
            this.transactions.executeWithoutResult(status -> {
                this.orders.create(committed);
                assertThat(this.count(committed)).isEqualTo(1);
                this.assertAbsent(consumer, committed, Duration.ofSeconds(3));
            });
            assertThat(this.count(committed)).isEqualTo(1);
            this.assertReceived(consumer, committed, topic);
            final String rolledBack = this.newId();
            assertThatThrownBy(() -> this.transactions.executeWithoutResult(status -> {
                        this.orders.create(rolledBack);
                        assertThat(this.count(rolledBack)).isEqualTo(1);
                        assertThat(this.orders.find(rolledBack).id()).isEqualTo(rolledBack);
                        assertThat(this.cache.find(new OrderId(rolledBack))).isEmpty();
                        throw new IllegalStateException("验收回滚");
                    }))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("验收回滚");
            assertThat(this.count(rolledBack)).isZero();
            assertThat(this.cache.find(new OrderId(rolledBack))).isEmpty();
            assertThatThrownBy(() -> this.orders.find(rolledBack))
                    .isInstanceOf(com.kjs.wuli3.core.error.ErrorCodeException.class);
            this.assertAbsent(consumer, rolledBack, Duration.ofSeconds(5));
        }
    }

    private void assertReceived(final SimpleConsumer consumer, final String id, final String topic) {
        final List<JsonNode> received = this.receiveFor(consumer, id, Duration.ofSeconds(30), true);
        assertThat(received).as("订单 %s 应在 30 秒内收到事件", id).isNotEmpty();
        final JsonNode event = received.getFirst();
        assertThat(event.path("topic").asText()).isEqualTo(topic);
        assertThat(event.path("eventType").asText()).isEqualTo("order.created.v1");
        assertThat(event.path("eventId").asText()).isNotBlank();
        assertThat(event.path("occurredOn").asText()).isNotBlank();
        assertThat(event.path("payload").path("orderId").asText()).isEqualTo(id);
    }

    private void assertAbsent(final SimpleConsumer consumer, final String id, final Duration window) {
        assertThat(this.receiveFor(consumer, id, window, false))
                .as("订单 %s 不应在观察窗口内发送事件", id)
                .isEmpty();
    }

    private List<JsonNode> receiveFor(
            final SimpleConsumer consumer, final String id, final Duration window, final boolean stopOnMatch) {
        final List<JsonNode> matches = new ArrayList<>();
        final long deadline = System.nanoTime() + window.toNanos();
        try {
            do {
                for (final MessageView message : consumer.receive(16, Duration.ofSeconds(15))) {
                    final String body =
                            StandardCharsets.UTF_8.decode(message.getBody()).toString();
                    final JsonNode event = this.json.readTree(body);
                    if (id.equals(event.path("payload").path("orderId").asText())) {
                        assertThat(message.getKeys())
                                .contains(event.path("eventId").asText());
                        matches.add(event);
                    }
                    consumer.ack(message);
                }
            } while (System.nanoTime() < deadline && (!stopOnMatch || matches.isEmpty()));
        } catch (final Exception failure) {
            throw new AssertionError("RocketMQ 接收失败，不能将基础设施异常视为没有事件", failure);
        }
        return matches;
    }

    private JsonNode success(final ResponseEntity<JsonNode> response) {
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        final JsonNode body = Objects.requireNonNull(response.getBody());
        assertThat(body.path("code").asText()).isEqualTo("0");
        assertThat(body.path("timestamp").asLong()).isPositive();
        return body;
    }

    private String failure(final ResponseEntity<JsonNode> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        final JsonNode body = Objects.requireNonNull(response.getBody());
        assertThat(body.path("code").asText()).isNotBlank().isNotEqualTo("0");
        assertThat(body.path("message").asText()).isNotBlank();
        return body.path("code").asText();
    }

    private String newId() {
        final String id = "it-" + UUID.randomUUID();
        this.ids.add(id);
        return id;
    }

    private int count(final String id) {
        final Integer count =
                this.jdbc.queryForObject("SELECT COUNT(*) FROM it_orders WHERE id = ?", Integer.class, id);
        return Objects.requireNonNull(count);
    }

    private String cacheKey(final String id) {
        return this.environment.getRequiredProperty("it.order.resource-prefix") + ":order:" + id;
    }

    private static void await(final CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new AssertionError("等待释放验收锁超时");
            }
        } catch (final InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError("等待锁时中断", failure);
        }
    }
}
