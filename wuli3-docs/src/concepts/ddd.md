---
description: 七模块 DDD 工程中的依赖方向、仓储职责和扩展边界。
---

# DDD 边界

DDD 约定的价值不是目录整齐，而是让业务规则有稳定的拥有者。模块越多，越需要先判断职责，再决定是否抽接口或复制对象。

## 依赖方向

![DDD 模块源码依赖方向](/ddd-boundaries.svg)

箭头表示源码依赖，从使用方指向被依赖模块。图中展示主要依赖；完整允许范围以构建约定为准。运行时 app 调用仓储接口、infra 实现接口，并不需要 app 依赖 infra。

domain 不依赖 Spring；app 不依赖 infra；adapter 不绕过 app 直接依赖 infra；bootstrap 负责最终装配。允许依赖表示可达范围，不表示每个模块都应声明所有依赖。

## 查询、写入和外部能力

| 问题 | 契约放在哪里 | 实现放在哪里 |
| --- | --- | --- |
| 列表、详情、统计、分页 | app 的 XxxQueryRepository | infra.persistence |
| 聚合修改与保存 | domain 的 XxxRepository | infra.persistence |
| 服务间调用、外部系统调用 | domain.gateway.XxxGateway | infra.gateway.XxxGatewayImpl |

查询仓储可以联表和投影，但不能借查询名义绕过权限或修改聚合。持久层访问使用 Repository / QueryRepository，外部访问（含跨系统查询）统一通过 Gateway。
ACL（防腐层）是设计概念，不作为代码包名或类名；Gateway 使用本地业务语言，远端 DTO、状态和错误在 Gateway 实现中转换。

## 什么时候需要独立对象

只有契约、生命周期、安全、格式或业务语义不同，才拆 Request、DTO、Response 或 PO。HTTP 不默认强制四层转换；领域聚合和数据库实体不能直接传播给外部调用方。

可信操作人、租户和权限版本从上下文获取，不能因为复用请求对象就让客户端覆盖。

## 门禁能帮你守住的边界

DDD 约定插件会检查依赖声明和编译后类型引用；infra 引用 app 类型需要 QueryRepository、Query 或 DTO 后缀，引用 api 只允许 dto 包。它不能判断一个 SQL 是否只读、一个 Service 是否缺少业务规则，仍需要代码审查与用例测试。

更完整的生成器和迁移说明见[生成器与项目骨架](/getting-started/generator)。
