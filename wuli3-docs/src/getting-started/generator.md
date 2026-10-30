---
description: 生成器参数、七模块职责和架构门禁的最小说明。
---

# 生成器与项目骨架

`wuli3-ddd-generator` 生成的是普通 Gradle 工程。生成完成后不依赖生成器运行，工程自带构建约定、质量门禁和中文包说明。

## 参数如何影响骨架

`--service`、`--package` 和 `--domain` 为必填项，分别决定服务名、Java 基础包和首个领域。服务名应使用小写字母开头、允许数字与单连字符的形式。`--wuli3-version` 与 `--build-logic-version` 默认均为当前 `1.0.0`，可以分别选择实际已发布的库与插件版本。

| 参数 | 默认值 | 选择后会发生什么 |
| --- | --- | --- |
| `--persistence` | `none` | `mysql` 时加入 MyBatis-Plus PO、Mapper 和扫描配置 |
| `--messaging` | `none` | `rocketmq` 或 `rabbitmq` 时加入对应 starter，但不猜 topic/queue |
| `--rpc` | `none` | `dubbo` 时生成 app facade 的发布能力 |
| `--registry` | `none` | `nacos` 作为 Dubbo 注册中心；必须同时启用 Dubbo |
| `--sort-fields` | `id,status` | 生成查询排序白名单 |

消息、数据库和注册中心选项只生成接入边界，不代表真实设施已经准备好。应用仍需补充连接、表、拓扑、认证和部署配置。

## 七个模块各自承担什么

![生成工程的主要源码依赖](/ddd-boundaries.svg)

箭头从使用方指向被依赖模块；只显示主要依赖，用来理解角色职责。完整约束见 [DDD 边界](/concepts/ddd)。

- `shared-kernel`：稳定共享标识、状态和值类型。
- `domain`：聚合、规则、仓储契约，不依赖 Spring。
- `api`：内部调用契约和传播 DTO。
- `app`：完整业务用例、查询契约和 Facade 实现。
- `infra`：Repository、Gateway 实现和技术配置。
- `adapter`：HTTP、消息消费和任务入口。
- `bootstrap`：启动、最终装配和部署配置。

以上是角色名。实际模块名包含模块前缀（默认省略服务名末尾的 `-service`），例如 `demo-order-app` 和 `demo-order-bootstrap`；Gradle 任务使用 `:demo-order-bootstrap:bootRun` 这样的路径。

入口层不编排多个局部事务；`app.service` 组织完整用例，读模型通过 QueryRepository，写模型通过领域聚合仓储。

## 架构门禁能证明什么

`verifyModuleDependencies` 和 `verifyTypeBoundaries` 会检查依赖方向、domain 的 Spring 禁令、infra 引用 app 的后缀白名单、共享内核依赖和查询仓储接口。它们不能证明业务规则完整、SQL 一定只读或外部协议已经可靠。

需要更严格地检查事务位置时开启 `wuli3.ddd.transaction-review.enabled=true`。这仍是静态位置检查，传播行为、回滚策略和代理自调用要靠测试验证。

完整参数和生成示例见仓库的 [生成器指南](https://github.com/weiran-tech/wuli3/blob/main/guide/wuli3-ddd-generator.md)。
