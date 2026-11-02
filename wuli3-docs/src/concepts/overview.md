---
description: Wuli3 的模块地图、责任分配和逐步阅读路径。
---

# 设计总览

Wuli3 是一组可以组合的 Java 能力，不是把所有基础设施封装成一个“大框架”。设计重点是让变化发生在正确边界：协议变化留在 adapter，业务规则留在 domain，用例编排留在 app，供应商 SDK 留在 infra。

## 一张模块地图

<FlowDiagram title="从入口到外部设施" :steps="[{ title: '入口', detail: 'HTTP、RPC、消息和任务' }, { title: '应用', detail: '校验、归属、事务、幂等' }, { title: '领域', detail: '聚合与业务不变量' }, { title: '端口', detail: 'Repository、Gateway、事件' }, { title: '设施', detail: '数据库、缓存、消息和第三方' } ]" />

同一个业务用例可以从 HTTP、RPC 或消息进入，但不应为每种入口复制一套业务编排。入口解析可信身份和协议，应用服务组织完整动作，领域对象决定状态是否允许改变。

## 能力与模块对照

| 要解决的问题 | 对应模块 | 下一步 |
| --- | --- | --- |
| 版本对齐、工程生成、质量门禁 | dependencies、ddd-generator、wuli3-build-logic | [创建工程](/getting-started/generator)、[构建验证](/engineering/build) |
| 错误、时间、ID、JSON、统一分页 | core、json、pagination | [基础契约](/capabilities/foundation) |
| HTTP 入口、响应、资源动作权限 | web starter、permission、permission starter | [Web 与权限](/capabilities/web-security) |
| 数据库、缓存、搜索和文档存储 | mysql、redis、elasticsearch、mongodb starter | [数据访问](/capabilities/data) |
| 请求、线程、任务与协议状态 | context-propagation | [上下文传播](/capabilities/context) |
| 本地通知、事务提交后发送、远程消息 | event-core、event、rocketmq、rabbitmq starter | [事件与消息](/capabilities/events) |
| 内部 RPC 与对象存储 | dubbo、aliyun starter | [外部集成](/capabilities/integrations) |
| 日志、关闭、审计和 Agent 观测适配 | core、audit-log、opentelemetry starter | [运行与运维](/capabilities/operations) |

表内 starter 均为 `wuli3-<能力>-spring-boot-starter`，纯 Java 模块为 `wuli3-<能力>`。按需组合组件，核心模块可以在不使用 Spring 的代码中复用。

## 三个稳定判断

### 这是请求边界还是内部不变量？

外部输入用统一 Asserts 转成错误码异常；构造器依赖、Bean 装配失败和私有方法不变量仍属于内部错误。不要用 IllegalArgumentException 掩盖请求输入的统一错误语义，也不要把内部不可达状态伪装成客户端错误。

### 这是查询投影还是聚合修改？

查询可以直接返回适合列表和分页的投影；修改必须加载聚合、执行业务行为并保存。CQRS 在这里表示职责分离，不要求分库、异步或固定 Handler 模板。

### 这是机制还是业务事实？

Wuli3 提供上下文传播、错误映射、事件路由、Redis 操作等机制。认证凭据、租户关系、数据归属、表结构、消息拓扑和重试策略仍是应用事实，需要应用明确实现和验证。

## 推荐阅读顺序

1. [快速入门](/getting-started/quick-start)：先运行一个无中间件服务。
2. [基础契约](/capabilities/foundation)：理解错误、JSON 和分页。
3. [请求生命周期](/concepts/request-lifecycle)：看这些契约如何串进一次请求。
4. [DDD 边界](/concepts/ddd)：把新代码放进正确模块。
5. 按需阅读 [Web 与权限](/capabilities/web-security)、[数据访问](/capabilities/data)和[事件与消息](/capabilities/events)。

设计约定不是业务正确性的证明。每个 starter 页面都包含“应用负责什么”和“验收重点”，请把它们转成项目测试和部署检查。
