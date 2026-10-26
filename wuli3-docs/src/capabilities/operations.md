---
description: 日志、配置加密、分阶段关闭、审计记录与 OpenTelemetry 的职责边界和部署方式。
---

# 运行与运维

运行基础设施的目标是让业务请求可追踪、敏感配置可保护、服务能够有序退出，并把关键操作事实交给独立审计链路。它们不会自动替应用完成采集、告警、密钥轮换或可靠消息。

## 日志与配置加密

`wuli3-core-spring-boot-starter` 默认提供日志属性、Jasypt 依赖和关闭协调器。日志默认只输出控制台文本，包含时间、级别、线程、应用名、requestId、trace/span 标识和 logger。

```yaml
wuli3:
  logging:
    enabled: true
    format: text
    file:
      enabled: false
```

支持 text、ECS、GELF 和 Logstash 格式。显式设置 `logging.config` 后，应用完全接管 Logback 配置；starter 不会再次注入默认属性。日志不负责采集、传输、审计存储或自动脱敏，密码和令牌仍由业务避免写入。

配置密文使用 Jasypt 标准 `ENC(...)`，主密码从环境变量、容器 Secret 或外部密钥系统注入。starter 不保存主密码，也不提供运行时轮换；修改算法、盐或输出格式前要确认历史密文兼容。

## 分阶段优雅关闭

应用可启用 Spring Boot Web graceful shutdown，并配置 Wuli3 关闭预算：

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 45s
wuli3:
  spring:
    shutdown:
      enabled: true
      phase-timeout: 30s
      phases:
        drain-async-tasks: { timeout: 30s }
        await-remote-ack: { timeout: 10s }
        close-clients: { timeout: 5s }
```

典型阶段顺序是排空异步任务、等待远程确认、关闭客户端。流量摘除和 Web Server 停止由部署系统与 Spring Boot 负责，不属于 Wuli3 阶段。

业务组件通过 `ShutdownHook` Bean 和 `@RegisterShutdownHook` 注册阶段与优先级。钩子应使用 `ShutdownContext.remaining()` 限制 IO，响应中断并保持幂等。单个钩子异常会记录并继续同阶段后续钩子；忽略剩余时间会拖延整个进程退出。

优雅关闭只覆盖正常终止信号。它不能挽救 `kill -9`、节点宕机、掉电或 OOM，也不能把内存中的消息变成可靠投递。关键消息仍需 Outbox、持久化队列或远端确认。

容器的 `terminationGracePeriodSeconds` 应覆盖应用预算并留出流量摘除时间。关闭超时时先看阶段与钩子日志，再检查无界等待、重复提交任务和中断处理。

## 审计记录

审计 starter 默认启用，可通过 `wuli3.audit-log.enabled=false` 关闭。它让业务提交“发生了什么”，并在生产者侧捕获主体、请求、来源 IP 与 trace 快照。同步调用 `AuditLogRecorder` 或使用 `@AuditLog` 注解，成功和失败结果都可记录；业务异常会在记录成功后继续传播。

注解的 `targetId` 与 `content` 支持只读模板表达式，可引用方法参数、返回值和异常。默认在事务提交后发布，避免回滚后产生成功审计；失败记录可通过 `recordFailure` 控制。事件发布异常会沿记录调用链传播，业务应为审计传输定义失败处理和可靠投递策略。

审计事件使用独立的事件选项类型。若容器提供 `AuditLogStore`，自动配置会创建进程内直写传输；否则必须注册支持该选项的远程 transport，不能把“已提交到内存线程池”当作可靠投递。

独立审计服务应以 `eventId` 幂等写入，保存生产者固化的主体和链路快照。starter 不提供查询 API、展示权限或存储实现；这些是审计服务的职责。

## OpenTelemetry

OpenTelemetry Java Agent 是 SDK、自动埋点、采样、上下文和 OTLP 导出的唯一所有者。starter 只读取当前 Trace，并提供业务 Counter、Histogram 和 Gauge 记录器。

```bash
OTEL_SERVICE_NAME=orders \
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318 \
java -javaagent:/opt/opentelemetry-javaagent.jar -jar orders.jar
```

没有 Agent 时 API 自动退化为 no-op，应用仍可启动。业务指标属性必须是有限低基数值；禁止把 traceId、requestId、订单号、用户标识或异常消息作为指标标签。

日志中的 `trace_id` 与 `span_id` 由 Agent 管理，业务不应从 MDC 反向构造 Trace。审计和业务需要链路标识时使用 `TraceContextAccessor`。

## 验收重点

验证自定义 logging.config 的接管行为、密文无法在源码泄露、关闭预算和客户端钩子、审计主体快照不可被消费者重算、无 Agent 时 no-op，以及指标标签基数受控。

构建和模块质量门禁见[构建与验证](/engineering/build)，部署边界见[部署与发布](/engineering/deployment)。

## 源码与详细指南

- [core Spring starter 指南](https://github.com/weiran-tech/wuli3/blob/main/guide/wuli3-core-spring-boot-starter.md)
- [审计记录器实现](https://github.com/weiran-tech/wuli3/blob/main/wuli3-audit-log-spring-boot-starter/src/main/java/com/kjs/wuli3/audit/internal/DefaultAuditLogRecorder.java)
