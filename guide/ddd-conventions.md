# DDD 领域驱动约定指南

本文适用于 `wuli3-ddd-generator` 2.1.0 生成的 Java 单体多模块工程，也作为后续业务开发的架构约定。
目标是让领域规则保持独立、业务用例易于复用、读写职责明确，同时避免机械分层和重复对象转换。

工程初始化见 [生成器使用指南](wuli3-ddd-generator.md)，构建约定见 [wuli3-build-logic](wuli3-build-logic.md)，完整目录示例见
[order-service](../examples/order-service)。本文区分设计约定与插件已实现的检查，不能把通过构建等同于业务设计正确。

## 1. 模块职责与依赖方向

| 模块 | 职责 | 允许依赖的业务模块 |
| --- | --- | --- |
| shared-kernel | 稳定共享标识、状态、值类型和约定 | 无 |
| domain | 聚合、实体、值对象、领域规则、聚合仓储与外部 Gateway 契约 | shared-kernel |
| api | 供内部调用方依赖的 RPC Facade 与传播 DTO | shared-kernel |
| app | 完整业务用例、Facade 实现、查询仓储契约 | api、domain、shared-kernel |
| infra | 仓储实现、外部能力适配、基础设施配置 | app、domain、api、shared-kernel |
| adapter | HTTP API、MQ 消费、Job 等入站协议适配 | app、api、domain、shared-kernel |
| bootstrap | 启动、配置选择、最终组装 | 其余业务模块 |

表中是当前门禁使用的可达依赖白名单：允许的依赖不代表每个模块都应全部声明。优先保留实现所需的最小依赖。
禁止逆向依赖，例如 domain → app、app → infra、adapter → infra，以及 infra → adapter。

调用方向与源码依赖方向不同：app 调用 Repository、QueryRepository 或 Gateway 接口，infra 实现接口，因此源码依赖仍然是 infra → app/domain。

```text
HTTP Api ──────────────┐
MQ Consumer ───────────┤
Job ──────────────────┼──> app.service.<领域>
RPC -> app.FacadeImpl ─┘          ├── 查询：app.QueryRepository ← infra 实现
        实现 api.Facade           ├── 写入：domain.Repository ← infra 实现
                                └── 外部能力：domain.gateway.CustomerGateway ← infra 实现
```

## 2. 目录与命名

以订单领域为例：

```text
api
├── facade/OrderFacade
└── dto/order/OrderStatusDto

adapter
├── api/order/OrderApi
├── mq
└── job

app
├── facade/order/OrderFacadeImpl
├── service/order/OrderService
└── repository/order/OrderQueryRepository

domain
├── gateway/CustomerGateway
└── order
    ├── Order
    ├── OrderItem
    ├── OrderRepository
    └── OrderPolicy

infra
├── config/OrderPersistenceConfiguration
├── gateway/CustomerGatewayImpl
└── persistence/order
    ├── OrderRepositoryImpl
    ├── OrderQueryRepositoryImpl
    ├── mapper/OrderMapper
    └── entity/OrderPo
```

以上是组织示例，不要求一次性创建所有类。生成器只提供初始领域的查询样例；聚合仓储当前示例只有加载方法，
真实写用例需要按聚合语义补充保存契约与实现，不应把示例理解为完整订单系统。

所有约定包都保留中文 `package-info.java`，即使暂时没有实现，例如 `adapter.mq`、`adapter.job`、`domain.gateway` 和 `infra.gateway`。
包说明应包含职责、依赖边界、命名约束和禁止事项，并按仓库规范记录作者、创建时间。外部系统子包按实际需求增加。

## 3. Service 是完整用例的核心

`app.service` 按业务领域分包，一个领域包内允许多个 Service，按内聚业务职责拆分。

- 简单业务可以先使用一个 `OrderService`。
- 下单、取消、查询形成独立职责后，可拆为 `OrderPlacementService`、`OrderCancellationService`、`OrderQueryService`。
- 不强制按内部/外部划分，不要求 CommandService 与 QueryService 成对出现。
- 不强制每个 Service 都抽接口或创建 Impl，也不要求一个方法对应一个 Service。

HTTP Api、FacadeImpl、MQ 消费者和 Job 是薄入口，处理协议、可信身份、输入适配，再调用应用用例。
HTTP 与 RPC 执行相同业务时复用同一 Service，不各自编排一遍库存检查、创建订单和保存操作。

多个操作必须共同提交时，应由完整应用用例组织，而不是由 Api 连续调用几个独立事务服务拼接。
业务差异确实存在时使用业务名称表达，例如“代客下单”，不要只以调用渠道区分实现。

## 4. CQRS 与仓储

CQRS 在这里体现为读模型与写模型职责分离，不要求读写分库、异步消息或固定 Handler 结构。

| 路径 | 契约归属 | 数据与行为 |
| --- | --- | --- |
| 查询 | app 的 QueryRepository | 直接查询详情、列表、分页或统计投影，可以联表，不强制还原聚合 |
| 写入 | domain 的 Repository | 加载聚合、执行领域行为、保存聚合，维护业务不变量 |

app 只定义查询仓储接口，但可以调用 domain 的聚合仓储。写操作加载聚合属于正常写用例，不违反读写分离。
不要把查询 DTO 当作聚合修改，也不要向 QueryRepository 增加写入能力绕过领域规则。

当前插件检查 app.repository 的顶层类型必须为接口；它不会根据方法名或 SQL 判断是否真的只读。
因此 `save()`、修改型 SQL、查询是否绕过权限等仍需要代码审查。

## 5. infra 对 app 的访问限制

infra 引用 app 类型时，简单类型名必须严格以以下后缀之一结束，大小写敏感：

| 后缀 | 示例 | 用途 |
| --- | --- | --- |
| QueryRepository | OrderQueryRepository | 本应用查询契约 |
| Query | OrderPageQuery | 查询条件 |
| DTO | OrderSummaryDTO | 应用层查询传播对象 |

例如 `OrderService`、`OrderFacadeImpl`、`CustomerGateway`、`OrderDto` 都不会因为位于 app 而获准被 infra 引用。
放行依据是类型后缀，不是 `app.repository` 整包豁免。不要靠改名规避职责边界。

这里的 `DTO` 是 infra → app 的严格后缀规则；api 模块中既有 `OrderStatusDto` 可以继续使用，infra 对 api 的限制是
只能访问 `api.dto` 包，不能访问 `api.facade`。两种规则的作用范围不同。

## 6. 对象契约与转换

不因为分层复制对象，只因为契约、业务语义、安全、格式或生命周期确有差异才拆分。

- HTTP 可以直接使用合适的应用输入和结果，不默认强制 Request → Command → DTO → Response。
- api 模块中的 RPC 传播对象统一使用 DTO；app 可以复用语义一致的 api DTO，并接受契约变化带来的耦合。
- 创建、修改、查询字段权限不同，不应为了复用而全部塞进一个对象。
- 对外接口需要独立演进、脱敏或特殊格式时，再引入独立 Request/Response。
- 不直接传播领域聚合或持久化实体，不把 HTTP/RPC 框架类型带入核心 Service。
- 可信操作人、租户等身份从认证上下文取得，不能因为复用对象而允许客户端任意覆盖。

避免同时使用语义不清的 dto/result/vo 分类。领域值对象直接使用业务名称，如 `Money`，避免与展示对象的 VO 混淆。

## 7. Domain 与 Shared Kernel

domain 按业务概念或聚合组织相关模型、规则和仓储，不使用全局 entity/service/repository 大包堆积所有领域。
包拆分不等于限界上下文拆分；不同上下文的术语和模型需要明确转换，不能仅因同处一个模块就直接修改彼此聚合。

**domain 禁止 Spring。** 不声明 Spring 依赖，不引用 Spring 注解或其他 Spring 类型；事务与装配由应用层或基础设施承担。
领域规则应由聚合、值对象或确有必要的领域服务表达，避免变成只有 getter/setter 的数据结构。

shared-kernel 只放跨领域稳定共享内容，不作为通用工具或业务逻辑的堆放区。当前允许：

- Wuli3 Core、必要的 JSpecify 注解。
- BOM 平台和必要的 Error Prone / NullAway 编译检查器。
- 编译期 Lombok：`compileOnly`、`annotationProcessor`；允许字节码中的 `lombok.Generated`。

Lombok 不作为运行时依赖引入；shared-kernel 不自动注入 SLF4J，仍禁止 Spring 等框架依赖。
生成工程根目录包含与仓库模板一致的 `lombok.config`，设置 equals/hashCode、toString 的 callSuper 和链式访问器。
使用 Lombok 仍需审查聚合身份、可变字段、继承和 equals/hashCode 语义，不能由注解代替领域设计。

## 8. Gateway 与基础设施配置

持久层访问使用 `XxxRepository` / `XxxQueryRepository`，实现放在 `infra.persistence`。
外部访问（包括服务间调用、外部系统调用）统一通过 `domain.gateway.XxxGateway`，由 `infra.gateway.XxxGatewayImpl` 实现。
ACL（防腐层）是设计概念，不作为代码中的包名或类名；Gateway 使用本地业务语义，实现负责远程调用、模型、状态和错误转换。
外部 SDK、远端 RPC DTO 不穿透为应用契约。跨系统查询也通过 Gateway，不归入本地查询仓储。

外部服务的 API 制品只在 `infra` 声明依赖和引用类型；不要通过 `api` 依赖导出给 app。
app Service 调用 `domain.gateway` 本地接口，infra 实现接口并将远端 DTO 转为本地结果或值对象；
不要直接将远端 DTO 映射为聚合来绕过领域不变量。本服务自己的 `api` 模块仍可供 app 实现 Facade。

门禁需要显式登记外部 API，不能根据制品名包含 api 自动判断：

```properties
# 精确 group:artifact，多个值用逗号分隔，不带版本
wuli3.ddd.external-api.coordinates=com.example.customer:customer-api
# 完整外部 Java 包前缀，包含子包；应与上述制品同步登记
wuli3.ddd.external-api.packages=com.example.customer.api
```

坐标检查直接生产依赖声明，包检查字节码引用。未登记的外部制品不会被自动识别；
bootstrap 可通过 infra 的传递依赖完成运行时组装，但不得直接声明或引用已登记的外部 API。
旧项目将 `app.acl` 接口迁移到 `domain.gateway`，命名为 `XxxGateway`；将 `infra.acl` 实现迁移到
`infra.gateway`，原 `XxxAcl` / `XxxAclImpl` 实现改名为 `XxxGatewayImpl`。同步迁移本地模型并调整 app/infra 导入。

MQ 消费属于 adapter.mq；业务执行过程中的消息发送属于出站基础设施能力。RPC FacadeImpl 在 app，Dubbo 选项下由 `@DubboService` 直接发布。

infra.config 拥有 Mapper 扫描、外部客户端等技术配置；扫描范围明确限定到对应 Mapper 包。
bootstrap 负责选择配置并最终组装，地址、凭据和运行参数通过外部环境提供。只有需要作为可复用组件发布时，才引入自动配置机制。

## 9. 事务边界与可选审查

完整业务用例的事务边界优先由 Service 管理。infra 仓储实现可以管理自身持久化操作的事务，
但仓储局部事务不能替代多个操作必须共同提交的应用事务。

```properties
wuli3.ddd.base-package=com.example.order
wuli3.ddd.transaction-review.enabled=false
# 可选：扩展事务包和基础设施仓储实现后缀（逗号分隔）
wuli3.ddd.transaction-review.allowed-packages=app.service,infra.persistence
wuli3.ddd.transaction-review.allowed-infrastructure-type-suffixes=RepositoryImpl
# 可选：扩展 infra 引用 app 类型的后缀白名单（逗号分隔）
wuli3.ddd.types.infra-app-allowed-suffixes=QueryRepository,Query,DTO
```

`verifyTransactionBoundaries` 默认关闭；开启后，仅允许以下位置直接声明 Spring `@Transactional`，类和方法均适用：

1. `<基础包>.app.service` 及其子包。
2. `<基础包>.infra.persistence` 及其子包中，以 `RepositoryImpl` 结尾的类，包括 `OrderQueryRepositoryImpl`。

Api、FacadeImpl、Job、Mapper、infra.config、infra.gateway 不在事务白名单中。即使类名叫 RepositoryImpl，位于 config/gateway 包也不允许。
不强制所有 Service 开启事务；查询是否需要只读事务根据一致性要求决定。

该审查仅检查直接注解位置，不推断组合注解、继承事务、编程式事务、传播行为、回滚规则、跨服务一致性或 Spring 自调用代理问题。
这些行为需要用例测试和代码审查验证。

事务允许包和基础设施实现类后缀可以通过 Gradle 属性扩展。属性使用相对基础包的包名，
例如 `app.service.order` 会匹配 `app.service`；`infra.persistence` 下的类型仍需匹配
`wuli3.ddd.transaction-review.allowed-infrastructure-type-suffixes`。未配置时保持上述默认规则。

## 10. 构建门禁及检查边界

插件 ID 为 `com.kjs.wuli3.ddd-conventions`，在标准七模块中先于 Java/Spring 约定应用，不替代语言构建插件。
生成项目已配置接线；业务工程不复制架构检查代码。三个校验任务按检查职责命名，统一归入 `verification` 分组，
由 `check` 调用；插件 ID 与 `wuli3.ddd.*` 配置前缀保持原名。

| 任务 | 自动检查内容 |
| --- | --- |
| verifyModuleDependencies | 生产依赖声明方向、domain Spring 禁令、shared-kernel 依赖白名单 |
| verifyTypeBoundaries | 字节码依赖方向、domain Spring 禁令、infra 后缀与 api.dto 边界、共享内核纯净、查询仓储接口 |
| verifyTransactionBoundaries | 开启时检查直接 Spring 事务注解的位置 |

类型和事务检查使用 ArchUnit core，只导入当前模块的生产类文件；不自动解析外部类路径，
缺失依赖仍按字节码中的类型名称校验。ASM 补充 ArchUnit 尚未建模的强转、数组创建等类型引用，
保持原有类型依赖检查范围。事务检查只识别类和成员上直接声明的注解，
没有类、查询仓储或事务注解时允许通过。三个任务保留 Gradle 配置缓存、增量检查和构建缓存支持。

```bash
./gradlew check
./gradlew check -Pwuli3.ddd.transaction-review.enabled=true
./gradlew :infra:verifyTypeBoundaries
./gradlew :domain:verifyModuleDependencies
```

基础包优先读取 `wuli3.ddd.base-package`，兼容回退到 `wuli3.conventions.nullaway.annotated-packages`，要求单个有效包名。
模块角色依据标准模块名识别，不应通过改模块名或把代码搬出项目基础包绕过门禁。

`infra` 引用 `app` 类型的后缀白名单可通过 `wuli3.ddd.types.infra-app-allowed-suffixes` 扩展，
适用于项目需要使用 `Projection`、`Assembler` 等明确应用层查询契约的场景。扩展白名单前应确认
该类型确实是基础设施所需的稳定契约，避免用配置掩盖错误的依赖方向。

声明检查覆盖生产 compile/runtime/annotationProcessor 配置，不等于完整传递依赖树审计；字节码检查不能发现反射字符串形成的动态依赖。
测试源码不作为生产架构边界扫描对象。插件也不验证业务命名是否诚实、查询 SQL 是否只读、领域规则是否完整、入口是否重复编排。

## 11. 开发与审查顺序

1. 明确业务语义、聚合边界和不变量，先选择承担职责的领域包。
2. 明确是查询投影、聚合操作还是外部系统能力，再选择 QueryRepository、Repository 或 Gateway。
3. 在 Service 实现完整用例，HTTP/RPC/MQ/Job 复用；仅在真实契约差异处转换对象。
4. 选择事务边界并测试失败回滚，不让入口层拼装原子操作。
5. 补齐中文 package-info、公共 API 边界说明及业务测试，执行受影响模块 check；提交前执行完整 check。

公共 API 的外部输入通过 Wuli3 `Asserts` 进入统一错误码链；内部装配不变量与请求校验分开处理。
遵守项目 Java 约定：不可重新赋值的变量使用 final，实例成员显式使用 this，生产时间类型使用 java.time，注释说明意图与约束。
