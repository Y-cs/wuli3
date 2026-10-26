---
description: 在请求、异步任务、RPC 和消息之间传播调用上下文，同时保持作用域可恢复、可过滤。
---

# 上下文传播

上下文不是“把 ThreadLocal 复制一份”这么简单。Wuli3 把不可变状态、当前线程绑定、快照和协议编码拆开，使同一套调用信息可以安全穿过线程池、RPC 和消息边界。

## 四个概念

| 概念 | 作用 | 是否改变当前线程 |
| --- | --- | --- |
| `ContextState` | 当前执行所需的完整不可变数据集合 | 否 |
| `ContextSnapshot` | 允许跨边界传播的数据子集 | 否 |
| `ContextProxy` | 在回调期间绑定指定状态 | 仅在回调内 |
| `ContextPropagator` | 在快照与协议字段之间编解码 | 否 |

创建代理不会立即改写当前线程。每次 `run`/`call`/`wrap` 建立独立作用域，回调结束后恢复进入前状态，即使回调抛出异常也会清理。

## 同步代码

```java
final ContextSnapshot snapshot = contexts.capture();
contexts.from(snapshot).run(() -> service.handle());
```

业务读取应使用 `ContextReader` 或专用 accessor；不要直接持有可变上下文对象。需要在当前状态上添加数据时，先派生新状态再执行，原状态保持不变。

## 异步任务

```java
final ContextProxy proxy = contexts.from(contexts.capture());
executor.execute(proxy.wrap(task));
```

`capture` 固定捕获时刻的数据，`from` 只还原传播资格允许的字段，不会把目标线程已有数据自动合并进去。任务完成后，线程池中的原状态恢复。

普通线程池和虚拟线程都需要显式包装。返回 `Future` 后继续执行的完成回调不自动属于原作用域；应为每个异步阶段建立合适的代理。

## 协议边界

出站路径是“当前状态 → 快照 → 协议字段”；入站路径是“协议字段 → 快照 → 代理 → 业务回调”。`ContextPropagator` 只做字段校验与转换，不绑定线程、不执行回调。

协议适配器负责三件事：确定来源是否可信、决定恢复哪些字段、把 `requestId` 在回调期间关联到日志 MDC。线程池线程离开回调时必须恢复原 MDC。

标准字段包括请求标识、来源 IP、主体类型、主体标识、主体名称和可选权限主体码。所有字段都使用 `X-Wuli3-` 前缀；未知同前缀字段也应视为保留字段，网关必须统一清洗。

## 身份与权限的传播边界

传播身份只表示上游已建立的认证上下文，不代表本地服务再次完成认证。网关清洗伪造字段并注入认证结果后，内部服务才可以恢复它。

权限主体码是可轮换授权版本。没有主体码时可以恢复认证身份，但受保护操作应拒绝鉴权。权限加载器必须使用当前主体码，不能从用户标识推导权限版本。

向第三方 HTTP、消息或租户边界发送请求前，应用应缩小编码器白名单，通常只保留 requestId；身份字段只能发往明确可信的内部服务。

## ThreadLocal 与 ScopedValue

默认 Spring 后端使用 ThreadLocal，在回调前绑定、`finally` 中恢复。自定义后端必须同时提供读取和绑定能力，二者必须共享同一存储；只替换其中一侧会得到“写入看得见、读取看不见”的错误。

JDK 21 的 `ScopedValue` 后端遵循同一接口语义。结构化任务会继承完整 ScopedValue 绑定；需要过滤传播内容时，先建立过滤快照，再在该作用域内创建任务作用域。

无论使用哪种后端，作用域都只覆盖同步回调。它不会自动延长到异步 Future 的任意完成阶段。

## 与日志和事件的关系

Web、Dubbo、RocketMQ 和 RabbitMQ 适配器在各自入口恢复上下文并绑定 requestId。事件信封只表达事件主题、类型、ID、时间和 payload，不携带协议 header；消息适配器把传播字段放在消息属性中。

审计记录在生产者侧捕获主体、请求和 trace 快照。消费者不能用自己的当前上下文重新推导操作人，否则异步处理会把“处理者”误记成“发起者”。

## 验收重点

验证嵌套作用域恢复、异常清理、线程池复用、空快照、过滤字段、重复身份头和异步完成回调。特别检查一次请求结束后，下一次复用同一线程看不到前一个请求的主体和 requestId。

具体消息与 RPC 适配见[事件与消息](/capabilities/events)和[外部系统集成](/capabilities/integrations)。

## 源码与详细指南

- [上下文传播指南](https://github.com/weiran-tech/wuli3/blob/main/guide/wuli3-context-propagation.md)
- [上下文管理入口](https://github.com/weiran-tech/wuli3/blob/main/wuli3-context-propagation/src/main/java/com/kjs/wuli3/propagation/ContextManager.java)
