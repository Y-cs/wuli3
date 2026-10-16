# wuli3-rabbitmq-spring-boot-starter 使用指南

该 starter 为事件模块提供基于 RabbitMQ 的 `RemoteEventTransport` 发送实现，并提供可手动使用的消息上下文恢复支持。

## 引入

```kotlin
dependencies {
    implementation("com.kjs.wuli3:wuli3-rabbitmq-spring-boot-starter")
}
```

模块会传递 `wuli3-event-spring-boot-starter`、`wuli3-context-propagation` 和 Spring Boot AMQP starter。

## RabbitMQ 配置

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

应用或基础设施需要自行声明与每个 `EventEnvelope.topic` 同名的 exchange，以及对应的 queue 和 binding；本 starter 不创建或修改 RabbitMQ 拓扑。

## 发布远程事件

```java
final EventEnvelope<OrderPaid> envelope =
        EventEnvelopeTemplate.of("orders", "order.paid.v1").wrap(payload);

final RabbitPublishOptions options = new RabbitPublishOptions()
        .withAfterCommit();

eventPublisher.publish(options, envelope);
```

传输使用 `EventEnvelope.topic` 作为 exchange，`EventEnvelope.eventType` 作为 routing key。事件信封仍以 JSON 作为 body，`eventId` 和 `eventType` 分别写入 AMQP 的 `messageId` 和 `type` 属性。

`RabbitPublishOptions` 支持 `withAsync()` 和 `withAfterCommit()`：

- `withAsync()` 在调用线程编码事件和上下文后，使用事件模块的 `eventTaskExecutor` 发送。
- `withAfterCommit()` 在当前 Spring 事务存在时于提交后发送。
异步任务启动失败会抛出 `SendFailedException`；已调度的后台发送失败会记录日志。该模块不将异步返回等同于 publisher confirm，也不实现 Outbox、重试、死信、延迟或顺序消息语义。

## 上下文传播

`ContextPropagator` 读取可选的 `ContextReader`，并按显式白名单将传播字段写入 AMQP headers。`EventEnvelope` 不承载传输 header，其 JSON body 只包含事件语义字段。

默认自动配置使用 `ContextPropagator.standardContextEncoder()`，当前会传播 `X-Wuli3-Request-Id`、`X-Wuli3-Origin-Ip`、
`X-Wuli3-Principal-Type`、`X-Wuli3-Principal-Id` 和 `X-Wuli3-Principal-Name`。因此该 starter 应只用于允许传播认证信息的可信消息边界。

若边界只允许传播调用标识，可显式覆盖 `ContextPropagator` Bean。同一个 `ContextPropagator` Bean 同时决定出站字段和入站可恢复字段。

消费适配器通过回调显式建立恢复作用域：

```java
rabbitContextSupport.runInScope(message.getMessageProperties().getHeaders(), () -> {
    listener.handle(message);
});
```

`runInScope` 不会自动注册或包装 RabbitMQ Listener。实际 Listener 仍应根据消息来源、线程模型、重试和死信策略决定调用时机。回调期间同时把 `requestId` 写入日志 MDC，作用域退出后按消费线程进入前的值还原。

## 验证

```bash
./gradlew :wuli3-rabbitmq-spring-boot-starter:test
./gradlew :wuli3-rabbitmq-spring-boot-starter:check
```

### 可信权限主体码

`AuthContext` 另携带 `X-Wuli3-Principal-Permission-Token`，表示可信入口确定的可轮换授权版本。
缺码时仍可恢复认证身份，但权限模块拒绝鉴权；非空码必须非空白。
所有标准传播字段现使用 `X-Wuli3-*`，不读取旧字段。Higress 外部入口须先按前缀（忽略大小写）删除全部伪造头，再写入认证结果。
具体清洗插件及配置在网关工程部署，参见 [权限模块的入口契约](wuli3-permission.md#higress-可信入口契约)。
