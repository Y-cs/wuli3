---
description: 请求进入、可信身份、权限加载、统一响应和错误可见性的协作方式。
---

# Web 与权限

Web starter 处理 Spring MVC 的入口与出口：创建调用上下文、装配 JSON、包装成功响应、转换异常。权限模块处理资源动作授权。认证来源、数据归属和业务规则由应用接入。

## 一次受保护的请求

<FlowDiagram title="受保护请求的处理路径" :steps="[{ title: '可信入口', detail: '验证身份并清洗外部传播字段' }, { title: '上下文', detail: '建立请求与主体信息' }, { title: '授权', detail: '加载当前权限并检查资源动作' }, { title: '业务用例', detail: '检查数据归属、状态与事务' }, { title: '响应', detail: '处理错误可见性与 JSON 输出' }]" />

这些阶段有独立职责。具有“订单查询”功能权限的用户，仍可能无权读取其他租户的订单；身份字段存在也不代表请求经过有效认证。

## 可信入口与登录

默认认证解析器读取可信内部 HTTP 的 `X-Wuli3-Principal-*` 字段。它适用于网关已经完成认证、业务服务处于可信网络内的部署。

外部网关必须先按大小写无关的 `X-Wuli3-` 前缀清除所有外部传入字段，再根据认证结果重新注入单值。前缀本身没有签名能力，业务服务应阻止非可信流量绕过网关。

直接接收公网请求、使用 Token、Session 或安全框架主体的应用，应实现 `AuthContextResolver` 并注册为 Bean，把验证后的主体转成框架上下文。框架不会代替应用验证访问令牌。

主体类型、标识和名称必须完整有效；权限主体码 `X-Wuli3-Principal-Permission-Token` 可缺失，但缺少它的身份不能通过权限鉴权。权限主体码用于标识可轮换的授权版本，不应从用户 ID 临时拼出。

`@LoginRequired` 标记需要登录的 Controller 或方法。缺少认证上下文时返回 401；未标记接口默认允许匿名。类级登录要求覆盖该类全部方法，没有单方法豁免。

## 来源 IP 与请求标识

默认创建调用上下文并关联 `requestId`。请求结束后恢复进入前的线程状态，避免线程复用串用身份和日志字段。

| 配置 | 默认行为与用途 |
| --- | --- |
| `wuli3.web.context.enabled` | 默认开启请求上下文 |
| `wuli3.web.context.request-id-header-name` | 默认 `X-Wuli3-Request-Id` |
| `wuli3.web.context.accept-external-request-id` | 默认允许接收外部请求标识 |
| `wuli3.web.context.request-id-max-length` | 默认最大长度 128 |
| `wuli3.web.context.trusted-proxy-cidrs` | 默认为空；配置可信代理来源范围 |

只有直接连接的 peer 命中可信代理网段，才会读取相应转发来源信息。可信代理配置控制来源 IP 解析，不是身份签名验证，不能用它替代网关认证和网络隔离。

## 权限目录与主体权限

权限分成两份数据：服务本地声明“有哪些资源和动作”，用户服务提供“当前主体拥有哪些动作”。前者启动时确定，后者在每次鉴权时通过应用加载器获得。

| 数据 | 谁维护 | 生命周期 |
| --- | --- | --- |
| 本地资源目录 | 业务代码中的权限枚举 | 启动扫描、校验并固定 |
| 主体权限快照 | 授权系统与应用加载器 | 按当前权限主体码加载 |
| 方法所需权限 | 类与方法上的声明 | 启动解析并缓存元数据 |

资源采用稳定资源码，动作使用显式位号。CRUD 占位 0 至 3，扩展动作占位 4 至 63；第 63 位使位图可能为负数，负数不是非法权限。不要用枚举声明顺序持久化动作。

应用必须实现 `PrincipalPermissionLoader`，输入是当前可信权限主体码及本服务资源范围，输出是与该主体码匹配的不可变权限快照。

加载器可以查询 Redis，未命中时回源用户服务并回填。缓存键、TTL、撤权失效和服务失败策略由接入层决定。空权限 Map 表示“成功查询但没有权限”，不能当作缓存未命中。

## 注解与程序鉴权

`@Permission` 用于经过 Spring 代理的 public 实例方法。类、方法、接口和父类上的声明会叠加，要求全部满足。启动期会检查引用，未登记的声明不会退回运行时猜测。

同类自调用、手动创建对象、private/static 方法不会经过普通 Spring AOP。此类调用路径需要直接使用 `PermissionChecker`；CGLIB 对 final 类和 final 方法同样有代理限制。

程序鉴权支持单项、全部满足或任一满足。一次组合判断只加载一次权限。框架缓存方法元数据，不缓存主体权限或最终授权结果，因此撤权能否及时生效取决于应用加载器。

```yaml
wuli3:
  permission:
    base-packages:
      - com.example.order
      - com.example.shared.permissions
    enforcement-enabled: true
```

显式 `base-packages` 替换默认扫描范围，应同时包含应用包和外部权限包。关闭 `enforcement-enabled` 后只保留资源目录，不再注册 Checker 与方法拦截器。

默认开启鉴权时，需要加载器与上下文读取器。已有 Web/Dubbo 后端应直接复用；独立配置必须保证读取器与绑定器指向同一份存储。

::: tip 登录与权限的响应不同
只使用权限注解时，匿名请求按权限模块错误处理，默认映射为 400。需要明确的 401 登录响应时，同时声明 `@LoginRequired`。授权失败的 HTTP 状态可通过 Web 状态扩展按产品契约调整。
:::

## 成功响应与已有接口兼容

引入 Web starter 默认启用成功响应包装和统一异常处理。成功业务码为字符串 `0`，响应包含 `message`、毫秒时间戳、`requestId` 与 `data`。

已包装响应不会再包装；文件、字节数组和流式响应保持原生输出，204/304 不写入响应体。`ResponseEntity` 默认保留状态与头，并继续包装其 body。

| 需求 | 选择 |
| --- | --- |
| 全局保留原成功格式 | 关闭 `wuli3.web.response.wrapper-enabled` |
| 保留已有异常处理 | 关闭 `wuli3.web.response.exception-handler-enabled` |
| `ResponseEntity` body 保持原样 | 关闭 `wrap-response-entity-body` |
| 单个接口原生成功响应 | `@NativeResponse` |
| 单个接口成功和错误均使用原生协议 | `@NativeResponse(ALL)`，错误为 `ProblemDetail` |

已有系统接入前应比较原有 HTTP 契约，尤其是客户端是否固定读取外层结构、文件下载和回调签名是否依赖原始 body。

## 错误输出与告警

默认业务异常依据责任映射：调用方问题为 400，服务方问题为 500。严重度只影响告警，可见性独立控制错误码和消息输出。结构化校验详情也必须服从可见性规则。

需要业务特定状态时实现 `WebErrorStatusResolver`；需要对接告警系统时实现 `ErrorAlertNotifier`。默认通知范围为 5xx 或严重度达到 `CRITICAL`/`FATAL` 的业务异常；未提供通知实现就不会发送外部告警。

Boot 管理的 HTTP 客户端 Builder 会安装上下文出站拦截器，传播身份与调用信息。使用这些客户端访问第三方地址前，应明确隔离传播行为，避免把内部身份字段带到非可信目标。

## 验收重点

至少覆盖：匿名与已登录请求、无权限与已撤权主体、加载器异常、跨租户访问、重复或伪造身份头、文件输出和隐藏内部错误。异步用例还要验证身份正确传播且任务完成后不残留。

继续阅读[上下文传播](/capabilities/context)，理解身份与调用信息跨线程时的生命周期。

## 源码与详细指南

- [Web starter 指南](https://github.com/weiran-tech/wuli3/blob/main/guide/wuli3-web-spring-boot-starter.md)
- [权限自动配置](https://github.com/weiran-tech/wuli3/blob/main/wuli3-permission-spring-boot-starter/src/main/java/com/kjs/wuli3/permission/autoconfigure/PermissionAutoConfiguration.java)
