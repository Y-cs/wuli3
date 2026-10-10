# wuli3-ddd-generator 使用指南

## 用途

`wuli3-ddd-generator` 用于生成单体多模块 DDD 服务。新工程通过制品库以命令行方式初始化，无需检出本仓库源码。生成结果是普通
Gradle 工程，生成完成后不依赖生成器运行。

仓库内的完整生成结果位于 [`examples/order-service`](../examples/order-service)，可以直接查看模块结构。

生成器会在工程根目录创建 `AGENTS.md`，包含模块边界、构建风格、编码风格、测试指南、提交规范、Git 使用、注释和配置安全约束。
规范使用生成时的服务名、领域名和 Java 包名，随内置模板发布，不依赖运行目录中的 `AGENTS.md`，也不包含生成器仓库的本机路径或
OMX 配置。

## JBang 初始化

外部项目无需检出本仓库，直接用 [JBang](https://www.jbang.dev/) 从 Maven 仓库拉取生成器执行：

```bash
jbang com.kjs.wuli3:wuli3-ddd-generator:0.1.3-SNAPSHOT generate \
  --service demo-web --package com.demo.web --domain order \
  --persistence none --messaging none --output .
```

JBang 默认从 Maven Central 解析。构件发布在内部仓库时，用 `--repos` 追加仓库地址：

```bash
jbang --repos <内部仓库URL>,mavenlocal \
  com.kjs.wuli3:wuli3-ddd-generator:0.1.3-SNAPSHOT generate \
  --service demo-web --package com.demo.web --domain order \
  --persistence none --messaging none --output .
```

### 参数

| 参数                    | 必填 | 可选值                             | 默认值           | 说明                                                         |
|-------------------------|------|------------------------------------|------------------|--------------------------------------------------------------|
| `--service`             | 是   | 小写字母开头，可含数字和单个连字符 | —                | 服务名，同时作为生成目录名                                   |
| `--package`             | 是   | 合法的小写 Java 包名               | —                | 生成源码的基础包名                                           |
| `--domain`              | 是   | 小写字母开头，可含数字和单个连字符 | —                | 领域名                                                       |
| `--persistence`         | 否   | `none`、`mysql`                    | `none`           | `mysql` 额外生成 MyBatis-Plus PO/Mapper 并引入 MySQL starter |
| `--messaging`           | 否   | `none`、`rocketmq`、`rabbitmq`     | `none`           | 非 `none` 时引入对应的 Wuli3 消息 starter                    |
| `--wuli3-version`       | 否   | 已发布的 Wuli3 版本                | `0.1.3-SNAPSHOT` | Wuli3 BOM 与 starter 版本                                    |
| `--build-logic-version` | 否   | 已发布的约定插件版本               | `0.1.3-SNAPSHOT` | Gradle 约定插件版本                                          |
| `--output`              | 否   | 目录路径                           | `.`              | 服务的父目录                                                 |

`--output` 是父目录不是工程目录：上例 `--output .` 生成的是 `./demo-web/`。目标目录必须不存在或为空，生成器不会覆盖已有业务文件。

### 示例

无外部基础设施的最小工程：

```bash
jbang com.kjs.wuli3:wuli3-ddd-generator:0.1.3-SNAPSHOT generate \
  --service demo-web --package com.demo.web --domain order \
  --persistence none --messaging none --output .
```

MySQL 持久化 + RocketMQ，生成到指定父目录：

```bash
jbang com.kjs.wuli3:wuli3-ddd-generator:0.1.3-SNAPSHOT generate \
  --service order-service --package com.example.order --domain order \
  --persistence mysql --messaging rocketmq --output ~/projects/order
```

RabbitMQ、固定 Wuli3 版本：

```bash
jbang com.kjs.wuli3:wuli3-ddd-generator:0.1.3-SNAPSHOT generate \
  --service pay-service --package com.example.pay --domain payment \
  --persistence mysql --messaging rabbitmq \
  --wuli3-version 0.1.3-SNAPSHOT --output .
```

生成完成后进入工程目录验证：

```bash
cd demo-web
./gradlew check
```

## 模块边界

生成工程包含七个 Gradle 模块：

```text
shared-kernel -> domain / api -> app -> infra
                              app -> adapter
                    infra / adapter -> bootstrap
```

- `shared-kernel` 只依赖 `wuli3-core`，放置跨领域共享的状态、标识和类型约束。
- `domain` 保存聚合、实体和值对象，只依赖 `shared-kernel`。
- `api` 保存对外 Facade、Command/Query 和响应 DTO，只依赖 `shared-kernel`。
- `app` 保存用例、应用服务以及 `port.in` / `port.out`。
- `infra` 依赖 `app` 和 `domain`，但只能使用 `app.port.out`，实现仓储和外部系统适配。
- `adapter` 依赖 `api` 和 `app`，负责 HTTP 等入站协议。
- `bootstrap` 依赖 `infra` 和 `adapter`，只负责 Spring Boot 启动和组装。

根工程统一向子模块应用 Kotlin 约定插件：`domain`、`api`、`shared-kernel` 使用
`com.kjs.wuli3.kotlin-conventions`，`app`、`infra`、`adapter`、`bootstrap` 使用
`com.kjs.wuli3.kotlin-spring-conventions`。约定提供 Java/Kotlin 21、Wuli3 BOM、测试依赖、JaCoCo、 Spotless + ktfmt
和静态质量门禁；Spring 约定额外配置 Kotlin Spring all-open 和 reflect。各模块构建文件只声明自己的业务依赖；
`bootstrap` 再额外应用 `org.springframework.boot`。Java 与 Kotlin 可在 `src/main/{java,kotlin}` 和
`src/test/{java,kotlin}` 中混编，Kotlin 编译先于 Java 编译。

## 架构门禁

生成工程使用两类互补门禁：

- `shared-kernel` 的 `verifySharedKernelDependencies` Gradle 任务检查生产依赖声明，只允许 `wuli3-core`；
- `SharedKernelArchitectureTest` 使用 ArchUnit 检查 Shared Kernel 字节码只能依赖自身、JDK 和 `wuli3-core`；
- `InfraArchitectureTest` 使用 ArchUnit 的依赖白名单检查 `infra` 字节码依赖。

`InfraArchitectureTest` 的规则如下：

- 允许 `infra`、`domain`、`sharedkernel` 和 `app.port.out`；
- 要求 `app.port.out` 只能包含接口，避免 Infra 实例化 App 类型；
- 允许必要的 JDK、Spring、MyBatis 类型；
- 禁止 `app.internal`、`app.service` 或 `app.port.in`；
- `new`、字段、方法参数、静态调用和继承等依赖形式都会被检查。

因此不建议使用 `forbidden-apis` 实现该规则。`forbidden-apis` 适合固定类或方法黑名单，不能表达“App 只有输出端口可被 Infra
依赖”的包级白名单。应用服务实现默认放在 `app.internal` 且保持 package-private，形成 Java 可见性和 ArchUnit 的双重约束。

## 验证

```bash
./gradlew check
./gradlew spotlessKotlinCheck
./gradlew spotlessKotlinApply
./gradlew :bootstrap:bootRun
```

MySQL 选项会额外生成 MyBatis-Plus PO/Mapper；消息选项会加入对应 Wuli3 消息 starter，未选择的基础设施不会生成。
消息中间件的连接和认证配置不会由生成器猜测，请按对应 starter 文档补充 `application.yml`。
