# wuli3-ddd-generator 使用指南

模块职责、业务开发与审查规范见 [DDD 领域驱动约定指南](ddd-conventions.md)。

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
| `--rpc`                 | 否   | `none`、`dubbo`                    | `none`           | `dubbo` 生成 Dubbo Facade 发布能力并引入 Dubbo starter       |
| `--registry`            | 否   | `none`、`nacos`                    | `none`           | `nacos` 作为 Dubbo 注册中心；必须同时设置 `--rpc dubbo`     |
| `--sort-fields`         | 否   | 逗号分隔的数据库字段名            | `id,status`      | 查询分页允许排序的字段白名单，生成到 infra 查询实现             |
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

生成工程会创建并保留 `logs/.gitkeep`，Docker 镜像也会预创建 `logs` 目录。模板默认启用文件日志，
这可保证 Spring Boot 回退到 JUL `FileHandler` 时日志锁文件能够创建；Wuli3 Core Starter 也会在日志初始化阶段创建配置文件的父目录。

## 模块边界

生成工程包含七个 Gradle 模块：

```text
shared-kernel -> domain / api -> app -> infra
                              app -> adapter
                    infra / adapter -> bootstrap
```

- `shared-kernel` 放置共享的状态、标识和稳定约定，允许 Wuli3 Core、必要的 JSpecify 注解与编译期 Lombok。
- `domain` 按领域组织聚合、实体、值对象、规则与聚合仓储。
- `api` 是内部 RPC 契约包，包含 `facade` 与按领域组织的 `dto`。
- `app.service` 按领域分包、按业务职责拆类；`app.facade` 实现 Facade 并委托 Service。示例用例包含
  `save`（新增或修改）、`findById`、`deleteById` 和 `page`；`save` 统一表达新增与修改。
- `api` 通过 `api` 依赖导出 `wuli3-pagination`，分页契约可供应用、HTTP 和 RPC 复用；MyBatis-Plus 类型只出现在 infra。
- `app.repository` 只定义 QueryRepository；`domain.gateway` 定义本地语义的外部业务能力。
- `infra` 实现两类仓储与 ACL；`infra.config` 管理 Mapper 扫描和技术 Bean。
- `adapter.api` 提供 HTTP 入口，类名以 Api 结尾；`adapter.mq`、`adapter.job` 提供消息消费与任务入口。
- `bootstrap` 负责启动、配置选择和最终组装；RPC 发布机制由接入/组装层配置。

`save` 保存调用方提供的标识和完整状态，不生成标识。MySQL 示例的 `insertOrUpdate` 先查主键再插入或更新，
不保证并发新增同一主键的原子 upsert；业务需要并发控制时应在仓储中使用数据库原子语句或明确的锁策略。
`persistence=none` 使用共享进程内存储，便于运行完整用例，数据不跨重启保留。

HTTP Api 和 FacadeImpl 复用 Service 中的完整业务用例及事务边界。Service 不按内部/外部或 Command/Query 强制拆分。
查询通过 app QueryRepository 返回投影，写入通过 domain Repository 加载聚合、执行业务行为并保存。
不因分层机械复制 Request/Response/Command/Result；语义一致时可复用 DTO，真实契约差异出现时才转换。

`domain.gateway` 与 `infra.acl` 隔离外部系统的模型、状态和错误；跨系统查询也属于 ACL，不归入本地查询仓储。
不生成 `app.acl`。外部服务 API 只在 infra 引入，通过 `wuli3.ddd.external-api.coordinates` 与
`wuli3.ddd.external-api.packages` 登记依赖坐标和包前缀；详细配置与迁移步骤见 [ACL 约定](ddd-conventions.md#8-acl-与基础设施配置)。
所有约定包（包括空的 mq/job/acl）均生成中文 `package-info.java`，明确职责、依赖与禁止事项。

### 领域模型与 Service 扩展

生成器只生成最小的状态聚合和传播 DTO，不猜测具体业务字段。新增领域字段时，应同步修改 `domain` 聚合、`api.dto` 传播对象、
`infra.persistence.<领域>.entity` 持久化对象及其转换逻辑；多表聚合应在同一 `RepositoryImpl` 中协调多个 Mapper，并由应用服务的事务边界包住完整用例。
生成器不会覆盖这些业务文件，便于从通用骨架演进为订单等具体模型。

默认生成一个领域 Service。业务复杂后按内聚职责拆分为 `XxxQueryService`、`XxxSaveService` 或其他用例 Service，入口和 Facade 只注入需要的 Service；
不要为了形式按 HTTP/RPC 或 Command/Query 机械复制。拆分后保留同一 domain Repository 与 QueryRepository 契约。

### 消息选项边界

`--messaging rocketmq` 或 `--messaging rabbitmq` 只为 `infra` 模块加入对应的 Wuli3 消息 starter，并保留
`adapter.mq` 消费入口包。生成器不会猜测 topic、exchange、queue、consumer group 或重试/死信策略，也不会生成带有
具体中间件注解的 Listener；这些配置属于业务协议和部署边界，应由应用在 `adapter.mq` 中实现。入口只负责协议解析、
幂等触发和上下文恢复，完整业务编排委托 `app.service`。

消息 starter 提供 `EventPublisher` 与远程 `RemoteEventTransport`，发送事件的用法和连接配置分别见
[RocketMQ 指南](wuli3-rocketmq-spring-boot-starter.md)或 [RabbitMQ 指南](wuli3-rabbitmq-spring-boot-starter.md)。
生成项目选择 `messaging=none` 时仍保留空的 `adapter.mq` 约定包，但不会引入消息依赖。

根工程统一应用 Java 约定插件：`domain`、`api` 使用 `com.kjs.wuli3.java-conventions`，
Spring 模块使用 `com.kjs.wuli3.spring-conventions`，各模块先应用 `com.kjs.wuli3.ddd-conventions`。
各模块共享 Java 21、BOM、测试和质量门禁；`bootstrap` 额外应用 Spring Boot 插件。
构建脚本继续使用 Gradle DSL 的 `.gradle.kts`，不向业务模块应用 Kotlin 编译插件。

## 架构门禁

DDD 架构检查由 `DddConventionsPlugin` 统一提供并接入 `check`，生成工程不复制门禁实现。

`verifyDddDependencies` 和 `verifyDddTypes` 对全部七模块检查生产依赖声明和字节码引用，按可达方向允许：

| 模块 | 允许依赖的业务模块 |
| --- | --- |
| shared-kernel | 无；仅允许 Core、JSpecify、编译期 Lombok、版本平台及必要编译检查器 |
| domain | shared-kernel |
| api | shared-kernel |
| app | api、domain、shared-kernel |
| infra | app、domain、api、shared-kernel |
| adapter | app、api、domain、shared-kernel |
| bootstrap | 其余业务模块 |

- domain 禁止声明 Spring 依赖或引用 Spring 类型。
- infra 引用 app 类型必须严格以 `QueryRepository`、`Query` 或 `DTO` 结尾，大小写敏感；引用 api 只允许 dto 包。
- app.repository 的顶层类型必须为接口，包说明不作为实现类检查。
- shared-kernel 保留生产依赖和类型白名单；违规依赖直接失败，不自动移除，不自动引入日志；允许编译期 Lombok 与生成的 lombok.Generated 注解，但禁止将 Lombok 声明为运行时依赖。
- 插件按标准模块名识别角色，先于 Java/Spring 约定应用；项目内类型依赖以 `wuli3.ddd.base-package` 为边界。

```properties
wuli3.ddd.base-package=com.example.order
wuli3.ddd.transaction-review.enabled=false
wuli3.ddd.transaction-review.allowed-packages=app.service,infra.persistence
wuli3.ddd.transaction-review.allowed-infrastructure-type-suffixes=RepositoryImpl
wuli3.ddd.types.infra-app-allowed-suffixes=QueryRepository,Query,DTO
```

基础包未配置时兼容回退到 `wuli3.conventions.nullaway.annotated-packages`，要求单一有效包名，拒绝空值和多个包。

可选任务 `verifyDddTransactions` 默认关闭。开启后仅允许 `app.service` 以及 `infra.persistence` 中以 `RepositoryImpl` 结尾的仓储实现，在类或方法上直接声明 Spring `@Transactional`。不要求所有 Service 声明事务。
该审查检查直接注解位置，不推断组合注解、继承事务、编程式事务、事务传播语义或运行时代理行为。

事务包和基础设施实现类后缀可通过上面的 Gradle 属性扩展；`infra` 引用 `app` 类型的后缀白名单也可通过
`wuli3.ddd.types.infra-app-allowed-suffixes` 扩展。未配置时保持默认规则，扩展前应确认新增类型确实属于稳定应用契约。

类型检查基于编译后字节码，不能识别反射字符串形成的动态依赖；声明检查不等同于完整传递依赖树扫描。

模板版本为 2.0.0，包结构不兼容旧模板；仅生成新项目，不覆盖或自动迁移已有项目。
使用时需要先发布包含 DDD 约定检查插件的 build-logic 版本，再通过 `--build-logic-version` 指定。

## Git 钩子

生成工程自带 `.githooks/pre-commit`，通过 `core.hooksPath` 生效，不写入不参与版本管理的 `.git/hooks`。
钩子对暂存文件执行 `spotlessApply` 并把格式化结果重新加入索引，随后执行 `forbiddenApis`；
单元测试、Error Prone、NullAway 和架构门禁由 `./gradlew check` 承担，放在 CI。

注意：`core.hooksPath` 保存在 `.git/config`，不随 clone 传输。为了让未手工配置的开发者也能用上钩子，
根工程注册了 `installGitHooks` 任务并挂到 `check` 与 `assemble`，执行 `./gradlew check`、`./gradlew build`
或 `./gradlew assemble` 时会自动对齐配置；也可单独执行：

```bash
./gradlew installGitHooks
```

任务只在 `.git` 存在且当前值不一致时写入，未找到 `.git` 时打印提示并跳过。生成器本身也会在生成时执行一次
`git init` 与同样的配置。

## 验证

```bash
./gradlew check
./gradlew spotlessCheck
./gradlew spotlessApply
./gradlew :bootstrap:bootRun
```

MySQL 选项会额外生成 MyBatis-Plus PO/Mapper 和显式 Mapper 扫描配置；消息选项会加入对应 Wuli3 消息 starter。
启用 Dubbo 时，bootstrap 模块生成 `DomainRpcConfiguration`（名称按领域替换），通过
`ServiceBean` 发布 Spring 管理的 Facade；app 和 api 不引入 Dubbo 注解或依赖。再启用 Nacos 时，
bootstrap 模块加入 `dubbo-registry-nacos` 运行时依赖，使用 `DUBBO_REGISTRY_ADDRESS` 环境变量设置
地址，默认 `nacos://127.0.0.1:8848`。`--registry none` 显式设置 `address: "N/A"`，仍可直接连接
Dubbo 端口调用。端口由 `DUBBO_PORT` 配置，默认 20880。

生成配置显式使用 `fastjson2` 序列化；RPC 请求、返回值及其嵌套对象必须保持可序列化，并由调用
双方共享兼容的契约版本。生成的 Spring 启动测试设置 `dubbo.enabled=false`，同时禁用 RPC 发布
配置，避免测试绑定端口或访问 Nacos；该测试验证业务装配，不代替真实 RPC 联调。未选择的基础
设施不会生成。
消息中间件的连接和认证配置不会由生成器猜测，请按对应 starter 文档补充 `application.yml`。

生成代码优先使用 Lombok：Spring Bean 用 `@RequiredArgsConstructor` 注入 final 依赖，聚合用 `@Getter` 和 `@Accessors(fluent = true)` 保留 `id()`、`status()` 访问方式。
DTO 和值对象保留 record；领域校验构造器及业务行为显式实现，不用 `@Data` 或公开 setter 绕过不变量。
Lombok 由 Java 约定插件提供编译期依赖和注解处理器，生成工程不可关闭 `wuli3.conventions.lombok.enabled`。
