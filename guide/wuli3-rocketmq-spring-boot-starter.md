# wuli3-rocketmq-spring-boot-starter 使用指南

该 starter 根据所选 RocketMQ 客户端为事件模块提供 `RemoteEventTransport` 发送实现，并提供可手动使用的消息上下文恢复支持。

## 引入

```kotlin
dependencies {
    implementation("com.kjs.wuli3:wuli3-rocketmq-spring-boot-starter")
}
```

模块会传递 `wuli3-event-spring-boot-starter`、`wuli3-context-propagation` 和 RocketMQ Spring Boot starter。

## RocketMQ 配置

```yaml
rocketmq:
  name-server: localhost:9876
  producer:
    group: order-service
```

以上是 v4 的配置方式；具体连接、认证和 producer 参数由 RocketMQ Spring Boot starter 管理。选择 v4 时没有 `RocketMQTemplate`，本模块不会注册 v4 transport。

## 客户端与包边界

- `com.kjs.wuli3.rocket.v4`：`RocketV4PublishOptions`、`RocketRemoteEventTransport`，自动配置位于 `v4.autoconfigure`。
- `com.kjs.wuli3.rocket.v5`：`RocketV5PublishOptions`、`RocketV5RemoteEventTransport`，自动配置位于 `v5.autoconfigure`。
- `autoconfigure.RocketCommonAutoConfiguration` 与 `internal`：共享编码、线协议和上下文传播。

两种 Transport 可同时注册，Publisher 按选项的具体类型路由。
v4 需要 `RocketMQTemplate` Bean；v5 需要运行时 Java Client 和业务提供的 `Producer` Bean。
业务提供同选项类型的 Transport 时，仅替换对应版本的默认实现。
移除原 `RocketPublishOptions` 与 `wuli3.rocketmq.client-version`，调用方需迁移到对应版本选项。
连接配置继续由 v4 官方 Starter 或业务创建的 v5 Producer 管理，不保留无实际配置项的 Properties 类。

v5 依赖是 `compileOnly`，应用需显式引入。starter 提供可覆盖的 `ClientServiceProvider` 工厂，
不创建 Producer，也不管理业务 Producer 的生命周期。没有 Producer 时不注册 v5 Transport。

```kotlin
dependencies {
    implementation("org.apache.rocketmq:rocketmq-client-java")
}
```

```java
@Bean(destroyMethod = "close")
Producer rocketV5Producer(final ClientServiceProvider clientServiceProvider) throws ClientException {
    final ClientConfiguration clientConfiguration =
            ClientConfiguration.newBuilder().setEndpoints("your-v5-endpoint:8081").build();
    return clientServiceProvider.newProducerBuilder()
            .setClientConfiguration(clientConfiguration)
            .setTopics("orders")
            .build();
}
```

应用负责 v5 Producer 的 endpoint、凭据和预声明 topic；调用 `setTopics(...)` 时应列出该 Producer 会发送的全部 topic。v5 选中后不会注入 v4 transport，即使应用同时配置了 `RocketMQTemplate`。

## 发布远程事件

```java
final EventEnvelope<OrderPaid> envelope =
        EventEnvelopeTemplate.of("orders", "order.paid.v1").wrap(payload);

final RocketV4PublishOptions options = RocketV4PublishOptions.builder()
        .afterCommit(true)
        .build();

eventPublisher.publish(options, envelope);
```

`RocketV4PublishOptions` 推荐通过 `builder().async(true).afterCommit(true).build()` 一次构造，
构建器设置字段时复用自身，`build()` 才创建不可变选项。固定配置可提前构造并复用；构建器不能跨线程共享。
支持同步、异步、顺序和精确延迟发送，但精确延迟不能与
`async` 或 order key 组合。v4 支持异步顺序发送；Java Client v5 不支持异步 FIFO，选择 v5
时 `async` 不能与 order key 组合。编码器要求：

- topic 长度不超过 127 字节。
- topic 只能包含字母、数字、`%`、`-` 和 `_`。
- 非正数 delay 和空白 order key 会在构造选项时被拒绝。

## 上下文传播

`ContextPropagator` 读取可选的 `ContextReader`，并按显式白名单把传播字段写入 `RocketMessageWrapper.headers`。`EventEnvelope` 不承载传输 header，其 JSON body 只包含事件语义字段。

默认自动配置使用 `ContextPropagator.standardContextEncoder()`，当前会传播 `X-Request-Id`、`X-Origin-Ip`、
`X-Principal-Type`、`X-Principal-Id` 和 `X-Principal-Name`。因此该 starter 应只用于允许传播认证信息的可信消息边界。

若边界只允许传播调用标识，应显式覆盖该 Bean：

```java
@Bean
ContextPropagator rocketMqContextEncoder() {
    return new ContextPropagator(List.of(new InvocationContextCodec()));
}
```

同一个 `ContextPropagator` Bean 同时决定 `RocketMessageWrapperEncoder` 的出站字段和 `RocketContextSupport` 的入站字段。缩小白名单后，入站恢复也只会接受对应字段。

消费适配器通过回调显式建立恢复作用域：

```java
rocketMqContextSupport.runInScope(messageExt.getProperties(), () -> {
    listener.handle(envelope);
});
```

`runInScope` 只解码字段编码器识别出的上下文并执行回调，不会自动注册或包裹 RocketMQ Listener。实际 Listener 仍应根据消息来源、线程模型、重试和死信策略决定调用时机。非法认证字段由 `AuthContextCodec` 忽略，避免消费适配器承担解析细节。

## 投递边界

- `afterCommit` 是提交后尽力发送，不是 Outbox 或可靠消息。
- 异步发送失败由 transport 记录日志，不会回滚已经提交的业务事务。
- 传播 header 属于 RocketMQ 消息属性，不进入 `EventEnvelope` 的 JSON body。
- 消费端的自动 Listener、幂等、重试和死信不在当前模块范围内；上下文恢复通过 `RocketContextSupport` 显式完成。

## 验证

```bash
./gradlew :wuli3-rocketmq-spring-boot-starter:test
./gradlew :wuli3-rocketmq-spring-boot-starter:check
```

## 发送回调

v4 使用 `RocketV4PublishOptions.builder().sendCallback(callback).build()`，回调是 SDK 原生 `SendCallback`。
v5 使用 `RocketV5PublishOptions.builder().sendCallback((receipt, failure) -> { /* 业务处理 */ }).build()`，
回调类型为 `BiConsumer<SendReceipt, Throwable>`：成功时只有 receipt，失败时只有 failure。
两者均支持同步与异步；`async(true)` 开启异步发送。v4 精确延迟不支持异步或顺序组合，
v5 支持异步延迟，但不支持异步 FIFO 或顺序延迟。

同步回调在发送线程执行，发送失败通知回调后仍抛异常；异步通过 SDK 完成通知触发回调。
回调运行时异常单独记录日志，不覆盖发送异常，也不会把成功回调异常再报告为发送失败。
未指定回调时保留默认失败日志。批量发送每条事件共用回调，业务负责线程安全与事件关联。
参数校验或异步启动失败直接抛异常，不额外触发回调；事务回滚未发送时不触发回调。
v4 的 SendResult 状态由业务判断，回调成功不等于消费成功。此功能不提供持久重试或可靠投递保证。

## v5 配置

基础 v5 Producer 参数可以放在配置文件中，Starter 会在没有业务自定义 `Producer` Bean 时自动创建：

```yaml
wuli3:
  rocketmq:
    v5:
      endpoints: localhost:8081
      access-key: ${ROCKETMQ_ACCESS_KEY:}
      access-secret: ${ROCKETMQ_ACCESS_SECRET:}
      security-token: ${ROCKETMQ_SECURITY_TOKEN:}
      topics:
        - orders
      request-timeout: 10s
      ssl-enabled: false
      namespace: order-service
      max-startup-attempts: 3
      max-attempts: 3
```

业务代码只需使用 `RocketV5PublishOptions`，不再负责连接地址、凭据和主题列表。凭据建议通过环境变量占位符注入。若需要自定义认证、事务检查器、多个 Producer 或特殊生命周期，业务可以声明自己的 `Producer` Bean；Starter 会自动让位。
