# 扩展点实战：上下文、Web 边界与基础设施端口

本文面向已经完成项目创建的使用者。依赖与启动步骤见 [使用教程](README.md)，模块完整说明见 [guide 索引](../README.md)。下面的代码区分完整类和方法内片段；业务认证、数据库写入等能力仍由应用实现。

## 1. 先判断扩展点在哪一层生效

| 扩展方式 | 典型入口 | 如何生效 |
| --- | --- | --- |
| 纯 Java 组合 | `ContextManager`、`ContextPropagator`、`RoutingEventPublisher` | 自己创建实例、传入依赖或调用注册方法，不依赖 Spring 扫描 |
| Spring Bean 替换默认实现 | `AuthContextResolver`、`RequestIdResolver`、`WebErrorStatusResolver` | 在应用扫描范围内声明 `@Configuration` 和 `@Bean`；对应默认 Bean 通过缺失条件退让 |
| Spring 收集多个实现 | `WebErrorMapper`、`ErrorAlertNotifier`、`RoutingEventTransport<?>` | 注册实现 Bean，由对应自动配置或处理链收集；排序、首个命中等规则依扩展点而定 |
| 协议适配 | `ContextCodec`、远程事件传输 | 完成编码、解码、信任校验及实际发送；仅声明一个接口实现并不等于协议已经接通 |

特别注意：`ContextCodec` Bean 不会被默认 Web 自动配置自动收集。默认 `ContextPropagator` 只装配标准调用上下文和身份上下文编码器；要增加编码器，需要显式创建自己的 `ContextPropagator` Bean。

## 2. 核心扩展按场景阅读

上下文作用域、自定义 Codec、异步和事件协作已展开为 [上下文与事件](context-events.md)。
身份解析、请求参数、统一响应与业务应补齐的内容见 [Web 接入](web.md)；错误状态码和权限加载见 [错误与权限](errors-permissions.md)。
资源解析器和脱敏策略见 [JSON 实战](json.md)。下文集中说明其他端口与优雅关闭。

## 3. 事件、审计、权限与关闭扩展导航

| 能力 | 应用需要提供什么 | 生效与边界 | 详细说明 |
| --- | --- | --- | --- |
| 纯 Java 事件传输 | `EventTransport<PO>` | 自己调用 `RoutingEventPublisher.register(...)`；按发布选项的具体类型路由，同类型重复注册会失败 | [事件核心](../wuli3-event-core.md) |
| Spring 自动收集事件传输 | `RoutingEventTransport<PO>` Bean | 默认 `EventPublisher` 收集此标记接口；任意普通 `EventTransport` Bean 不会因此被自动注册；自定义 `EventPublisher` 后需自己负责装配 | [事件 starter](../wuli3-event-spring-boot-starter.md) |
| 跨进程事件适配 | `RemoteEventTransport<PO>` | 采用更严格的远程标记以参与远程缺失条件判断；传输层仍需实现真实投递，不能把接口返回等同于可靠落库 | [事件 starter](../wuli3-event-spring-boot-starter.md) |
| 审计服务存储 | `AuditLogStore` Bean | 自动创建对应 store-backed 传输；`append` 必须按 `eventId` 幂等写入；业务客户端仍需可用的审计投递通道 | [审计日志](../wuli3-audit-log-spring-boot-starter.md) |
| 当前主体权限查询 | `PrincipalPermissionLoader` Bean | 默认启用鉴权，需要 `ContextReader` 与加载器；缺失依赖拒绝启动；可信 `AuthContext` 还必须含权限 Token | [权限](../wuli3-permission.md) |
| 优雅关闭工作 | `ShutdownHook` Bean + 类型上的 `@RegisterShutdownHook` | 默认由 core starter 收集；只加注解而没有注册 Bean 不会生效 | [Core starter](../wuli3-core-spring-boot-starter.md) |

本地业务事件可直接使用 Spring `ApplicationEventPublisher` 与 `@EventListener`。事务事件、异步事件、远程投递的语义不同，不能把普通事件发布当成事务 Outbox 或“恰好一次”保证。

权限模块的 `wuli3.permission.enforcement-enabled=false` 只保留目录能力并关闭 Checker/注解鉴权，不能作为缺少授权实现时的生产修复方案。权限与审计注解依赖 Spring 代理，类内部自调用不应被当作受拦截调用。

### 3.1 关闭自管线程池的示例

下面是完整钩子类，前提是应用已经声明名为 `reportExecutor` 的自管 `ExecutorService` Bean，并明确由这个钩子负责排空任务。

```java
package com.example.order;

import com.kjs.wuli3.spring.shutdown.RegisterShutdownHook;
import com.kjs.wuli3.spring.shutdown.ShutdownContext;
import com.kjs.wuli3.spring.shutdown.ShutdownHook;
import com.kjs.wuli3.spring.shutdown.ShutdownPhase;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 在阶段预算内排空应用自管的报表任务。
 *
 * @author 国杨 create on 2026/10/16 16:00
 */
@Component
@RegisterShutdownHook(phase = ShutdownPhase.DRAIN_ASYNC_TASKS, priority = 0)
public final class ReportExecutorShutdownHook implements ShutdownHook {
    private final ExecutorService executor;

    /** 注入由应用配置的报表执行器。 */
    public ReportExecutorShutdownHook(
            @Qualifier("reportExecutor") final ExecutorService executor) {
        this.executor = executor;
    }

    /** 停止接收任务，并在剩余预算内等待已提交任务。 */
    @Override
    public void shutdown(final ShutdownContext context) throws InterruptedException {
        this.executor.shutdown();
        try {
            if (!this.executor.awaitTermination(
                    context.remaining().toNanos(), TimeUnit.NANOSECONDS)) {
                this.executor.shutdownNow();
            }
        } catch (final InterruptedException exception) {
            this.executor.shutdownNow();
            throw exception;
        }
    }

    /** 返回关闭日志使用的稳定名称。 */
    @Override
    public String name() {
        return "report-executor";
    }
}
```

三个阶段依次是 `DRAIN_ASYNC_TASKS`、`AWAIT_REMOTE_ACK`、`CLOSE_CLIENTS`；同阶段优先级数值越小越先执行。预算需要钩子主动遵守，不是强制终止任意阻塞代码的保证；`shutdownNow()` 也只是请求任务中断。这个示例在超时后会放弃尚未开始的任务，只有接受这种业务语义时才采用。

## 4. 接入后怎样验证

1. 同步作用域中嵌套绑定并抛异常，确认外层上下文恢复。
2. 提交带请求 ID 的异步任务，确认任务能读到它；随后向同一个工作线程提交未包装任务，确认没有残留。
3. 公网请求伪造 `X-Wuli3-Principal-*`，确认身份只来自真实认证结果。
4. 用实际 Spring Boot builder 创建 HTTP 客户端，检查接收端得到预期传播字段；单独测试直接创建客户端的行为。
5. 对事件、审计做投递失败和重复事件测试；对权限做缺少身份、缺少 Token、未授权测试；对关闭钩子做超时和中断测试。

这些是业务应用需要执行的验收项。本页示例基于仓库 API 与自动配置源码核对，未声明已对你的认证、消息系统或存储完成端到端验证。
