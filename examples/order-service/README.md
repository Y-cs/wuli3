# order-service

由 `wuli3-ddd-generator` 模板 `2.0.0` 生成的单体多模块 DDD 服务。

开发者与 AI/Agent 的构建、编码、测试、提交、Git 和注释规范见根目录 [AGENTS.md](AGENTS.md)。

根工程统一应用 Java 约定插件：普通模块使用 `com.kjs.wuli3.java-conventions`，Spring 模块使用
`com.kjs.wuli3.java-spring-conventions`，各模块先应用 `com.kjs.wuli3.ddd-conventions`。
`bootstrap` 额外应用 Spring Boot 插件。Java 源码放在 `src/main/java`，测试放在 `src/test/java`。

## 模块边界

```text
HTTP Api / MQ / Job ────────────────> app.service.<领域>
RPC -> api.facade <- app.facade ────> app.service.<领域>
                                         ├─ 查询 -> app.repository -> infra 实现
                                         ├─ 写入 -> domain.Repository -> infra 实现
                                         └─ 外部访问 -> domain.gateway.XxxGateway -> infra.gateway.XxxGatewayImpl
```

- `api` 是内部 RPC 契约包，使用 Facade 和 DTO；`app.facade` 实现契约并委托 Service，RPC 发布由装配层负责。
- `app.service` 按领域组织，按业务职责拆分；不强制为每个领域创建 Command/Query 两类 Service。
- `adapter.api` 的 HTTP 类以 Api 结尾；`mq`、`job` 分别负责消费和任务入口，不承担完整业务编排。
- `domain` 按领域组织聚合、规则和聚合仓储；查询仓储由 app 定义，返回查询投影，不强制还原聚合。
- 持久化访问使用 Repository / QueryRepository；服务间调用与外部系统调用统一使用 Gateway，接口放 `domain.gateway`，实现放 `infra.gateway`。ACL 仅作为防腐设计概念，不用于包名或类型名。
- `infra.persistence` 实现仓储，`infra.gateway` 实现外部访问，`infra.config` 拥有技术配置，`bootstrap` 负责组装与启动。
- `shared-kernel` 保持纯净；插件检查生产依赖与类型依赖，不在项目中复制检查实现，也不静默删除违规依赖。
- 所有约定包均提供中文 `package-info.java`；空的 MQ、Job、Gateway 包也保留职责与依赖约束。

不因分层复制 Request/Response/Command/Result。契约同义时可复用 DTO，只有格式、安全、业务语义或独立演进确有差异时才转换。聚合和持久化实体不作为传播对象。

## 验证和启动

首次运行或升级 Wuli3 版本后，先在 Wuli3 仓库根目录将库、BOM 和约定插件发布到本地 Maven 仓库：

```bash
./gradlew publishToMavenLocal :wuli3-build-logic:publishToMavenLocal
```

`wuli3-build-logic` 是独立构建，需要显式发布，才能让本示例通过 `mavenLocal()` 解析对应版本的插件及其 marker。
发布完成后，在本示例目录执行：

```bash
./gradlew check
./gradlew :bootstrap:bootRun
```

DDD 约定插件检查七模块依赖方向、共享内核纯净性、domain 禁止 Spring、app 查询仓储接口及 infra 的应用类型后缀。
infra 引用 app 类型只允许 QueryRepository、Query、DTO 后缀（大小写敏感）；Gateway 契约属于 domain。
DDD 约定插件提供 `verifyModuleDependencies` 与 `verifyTypeBoundaries`，并统一接入 `check`。
可选事务审查 `wuli3.ddd.transaction-review.enabled=true` 仅允许 app.service 和 infra.persistence 中以 RepositoryImpl 结尾的仓储实现声明 Spring @Transactional，默认关闭。
选择 MySQL 时生成 Mapper、持久化配置与扫描；运行时连接信息由环境提供，启动测试使用替身隔离数据库。

模板 2.0.0 调整了入口、DTO、Service 和仓储包路径；只生成新项目，不自动迁移已有工程。
