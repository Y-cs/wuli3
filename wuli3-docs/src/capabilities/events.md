---
description: 从纯 Java 事件契约到 Spring 本地事件、RocketMQ 与 RabbitMQ 传输，明确事务和可靠性边界。
---

# 事件与消息

事件模块先定义稳定语义，再由 Spring 或消息中间件完成传输。这样领域和应用可以描述“发生了什么”，而不需要依赖某一种消息 SDK。

## 事件信封

事件信封包含逻辑主题、稳定类型、唯一 ID、发生时间和 payload。固定主题与类型应复用模板，事件类型一旦对外发布就应按版本演进。

信封不携带 HTTP、RPC 或消息 header。传播上下文属于传输适配器的职责，避免把协议细节写入跨系统业务模型。

## 发布选项决定能力

`PublishOptions` 是标记契约，具体 starter 定义自己的选项类型。路由器根据选项的具体类型查找对应 transport；未注册能力会明确抛出不支持异常。

这意味着“引入事件 starter”不等于“所有远程能力都可用”。应用必须引入对应传输、连接配置和主题拓扑，并在启动或验收时确认路由已注册。

## Spring 本地事件

引入事件 Spring starter 后，默认创建本地 transport、事件发布器和名为 `applicationTaskExecutor` 的虚拟线程执行器。应用可提供同类型 Bean 覆盖默认实现。

本地选项支持同步/异步和提交后发布：

| 选择 | 行为 |
| --- | --- |
| 同步 | 监听器在当前调用链执行，异常可传播回发布方 |
| 异步 | 使用 `applicationTaskExecutor`，发布方不等待监听完成 |
| `afterCommit` | 有活动 Spring 事务时注册到提交后；无事务时立即发送 |

提交后发布只能避免事务回滚后提前通知，不能保证进程崩溃后消息恢复。异步失败、幂等、重试和死信仍由应用负责。

## RocketMQ

RocketMQ starter 按 v4 与 v5 的具体选项类型分别注册 transport，两个版本可以同时存在。

### v4

v4 transport 在容器存在 `RocketMQTemplate` 时生效，连接和 producer 参数沿用官方 Spring Boot 配置。支持同步、异步、顺序和精确延迟等选项组合，但延迟不能与异步或 order key 任意组合，必须遵循选项校验。

### v5

v5 依赖由应用显式引入；可通过配置自动创建 Producer，也可提供自己的 Producer Bean。

```yaml
wuli3:
  rocketmq:
    v5:
      endpoints: localhost:8081
      topics: [orders]
      request-timeout: 10s
      ssl-enabled: false
```

配置 endpoints 时，topics 不能为空。凭据建议使用环境变量占位符；自定义 Producer 时，应用负责 endpoint、认证、topic 预声明和生命周期。v5 不支持与 order key 组合的异步 FIFO。

### 消息上下文

事件 body 只编码事件信封，传播字段写入消息属性。消费端通过 `RocketContextSupport` 显式运行恢复作用域；starter 不自动包裹 Listener，也不决定重试和死信策略。

默认编码器会传播 requestId、来源 IP 和可信主体字段。只允许传播调用标识的边界，应提供缩小后的 `ContextPropagator` Bean，并同时用于出站编码和入站解码。

## RabbitMQ

RabbitMQ transport 在存在 `RabbitTemplate` 时生效。应用必须自行声明 exchange、queue 和 binding；starter 不创建或修改拓扑。

事件主题映射到 exchange，事件类型映射到 routing key；消息 ID 和类型也会写入 AMQP 属性。异步发送使用 `applicationTaskExecutor`，提交后发送遵循事务存在时的 after-commit 语义。

Rabbit 的 publisher confirm、Outbox、重试和死信不由 starter 自动提供。异步任务已调度不等于远端已持久接收，业务需要为失败补偿设计可观测状态。

## 可靠性边界

下面几种语义不要混为一谈：

| 语义 | 当前 starter 是否保证 |
| --- | --- |
| 事务回滚后不发布 | `afterCommit` 在有事务时提供 |
| 进程崩溃后可恢复 | 不提供，需要 Outbox 或持久化队列 |
| 至少一次投递 | 由具体 transport 与应用重试策略共同决定 |
| 消费幂等 | 应用以 eventId 或业务键实现 |
| 消费成功后才确认 | 由 Listener 和中间件消费配置决定 |

关闭阶段可以等待已知异步任务或远程确认，但不能替代持久化投递。优雅关闭说明见[运行与运维](/capabilities/operations)。

## 验收重点

先用本地同步事件验证信封和监听器，再验证提交后、异步、连接失败和重复投递。接入 RocketMQ/RabbitMQ 后，额外检查主题拓扑、传播字段清洗、消费者恢复作用域、重试、死信和幂等。

下一步阅读[审计与可观测性](/capabilities/operations)，了解事件如何承载审计事实和运行指标。

## 源码与详细指南

- [事件 Spring starter 指南](https://github.com/weiran-tech/wuli3/blob/main/guide/wuli3-event-spring-boot-starter.md)
- [事件自动配置与路由装配](https://github.com/weiran-tech/wuli3/blob/main/wuli3-event-spring-boot-starter/src/main/java/com/kjs/wuli3/event/autoconfigure/EventAutoConfiguration.java)
