---
layout: home
title: Wuli3
titleTemplate: 设计从边界开始
description: 面向 Java 21 与 Spring Boot 的分布式项目脚手架底座。
hero:
  name: Wuli3
  text: 从业务边界开始。
  tagline: 基于 Java 21 与 Spring Boot 的工程底座。沿着错误、上下文、权限和事件，理解一次请求怎样成为可靠的业务用例。
  image:
    src: /architecture.svg
    alt: 入口经应用用例连接领域规则，基础设施实现出站端口
  actions:
    - theme: brand
      text: 快速入门
      link: /getting-started/quick-start
    - theme: alt
      text: 先理解整体设计
      link: /concepts/overview
features:
  - icon: ↗
    title: 从边界开始
    details: 用统一错误、上下文和权限契约，让入口、用例与基础设施各司其职。
    link: /concepts/request-lifecycle
  - icon: ◇
    title: 逐步接入
    details: 先用无中间件的最小工程跑通，再按真实一致性和吞吐需求选择 starter。
    link: /getting-started/adoption
  - icon: ✓
    title: 用证据交付
    details: Gradle 质量门禁、BOM 消费验证和真实集成测试共同守住发布边界。
    link: /engineering/build
---

<div class="home-grid">

## 这套文档怎样阅读

先完成 [快速入门](/getting-started/quick-start)，知道一个服务怎样生成、启动、调用和验证。然后阅读 [设计总览](/concepts/overview)，理解为什么核心模块保持无 Spring、为什么应用层组织完整用例。

接下来按实际问题选择能力页：错误与输出从[基础契约](/capabilities/foundation)开始；入口安全看 [Web 与权限](/capabilities/web-security)；数据一致性看 [数据访问](/capabilities/data)；跨线程和跨服务携带调用信息看 [上下文传播](/capabilities/context)。

最后再进入[事件与消息](/capabilities/events)、[外部系统集成](/capabilities/integrations)和[运行与运维](/capabilities/operations)。这些页面会反复标出应用必须自己决定或实现的部分。

## 一条业务链路

<FlowDiagram title="一次业务操作经过的边界" :steps="[{ title: '可信入口', detail: 'HTTP、RPC、消息和任务提供可信身份与协议输入。' }, { title: '应用用例', detail: '完成归属校验、事务与幂等，组织完整业务操作。' }, { title: '领域规则', detail: '由聚合和值对象保护业务不变量。' }, { title: '出站端口', detail: '经Repository、Gateway 和事件连接数据库与外部服务。' }]" />

Wuli3 提供稳定的机制和扩展契约，但不会替应用选择认证系统、租户隔离、表结构、消息拓扑或部署网络。文档把这些责任写在相应边界旁，避免把“有接口”误认为“已经接通”。

## 版本与范围

当前文档对应仓库版本 `1.0.0`、JDK 21、Spring Boot 3.5.x。模块版本由 `wuli3-dependencies` 管理；BOM 只管理版本，不会自动把所有 starter 放进应用。

</div>
