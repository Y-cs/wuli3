# {{service}}

由 `wuli3-ddd-generator` 模板 `{{templateVersion}}` 生成的单体多模块 DDD 服务。

开发者与 AI/Agent 的构建、编码、测试、提交、Git 和注释规范见根目录 [AGENTS.md](AGENTS.md)。

根工程统一应用 Java 约定插件：普通模块使用 `com.kjs.wuli3.java-conventions`，Spring 模块使用
`com.kjs.wuli3.spring-conventions`，各模块先应用 `com.kjs.wuli3.ddd-conventions`。
`bootstrap` 额外应用 Spring Boot 插件。Java 源码放在 `src/main/java`，测试放在 `src/test/java`。

## 模块边界

```text
HTTP Api / MQ / Job ────────────────> app.service.<领域>
RPC -> api.facade <- app.facade ────> app.service.<领域>
                                         ├─ 查询 -> app.repository -> infra 实现
                                         ├─ 写入 -> domain.Repository -> infra 实现
                                         └─ 外部业务能力 -> domain.gateway -> infra.acl
```

- `api` 是内部 RPC 契约包，使用 Facade 和 DTO；`app.facade` 实现契约并委托 Service，RPC 发布由装配层负责。
- `app.service` 按领域组织，按业务职责拆分；不强制为每个领域创建 Command/Query 两类 Service。
- `adapter.api` 的 HTTP 类以 Api 结尾；`mq`、`job` 分别负责消费和任务入口，不承担完整业务编排。
- 消息选项为 `{{messaging}}`；选择 RocketMQ 或 RabbitMQ 只引入对应 starter，不生成具体 Listener、拓扑或重试策略。
  消费入口应放在 `adapter.mq`，负责协议解析、幂等和上下文恢复，再委托 `app.service`。
- `domain` 按领域组织聚合、规则和聚合仓储；查询仓储由 app 定义，返回查询投影，不强制还原聚合。
- `infra` 实现仓储和 ACL，`infra.config` 拥有技术配置，`bootstrap` 负责组装与启动。
- `shared-kernel` 保持纯净；插件检查生产依赖与类型依赖，不在项目中复制检查实现，也不静默删除违规依赖。
- 所有约定包均提供中文 `package-info.java`；空的 MQ、Job、ACL 包也保留职责与依赖约束。

不因分层复制 Request/Response/Command/Result。契约同义时可复用 DTO，只有格式、安全、业务语义或独立演进确有差异时才转换。聚合和持久化实体不作为传播对象。

## 示例用例

`save` 合并新增与修改，写入完整的 `id/status` 状态；`findById` 查询单条，记录不存在时返回明确错误；`deleteById` 幂等删除；`page` 使用 `PageQuery` 和 `PageResult` 分页。
写操作经由领域仓储，查询投影经由应用查询仓储。排序仅允许 `{{sortFields}}` 中的字段，并追加主键保证稳定顺序。
`persistence=none` 使用进程内存储，重启后清空；MySQL 模式需先创建业务表并配置数据源。

生成配置中的 `sort.fields={{sortFields}}` 是查询排序白名单。扩展领域字段时，应同步扩展 DTO、聚合、PO 和仓储映射；多表聚合由同一仓储实现协调多个 Mapper，并复用 Service 的事务边界。
默认生成一个 Service；复杂领域可按内聚职责拆分为 Query、Save 或其他用例 Service，入口只依赖实际需要的 Service。

RPC 选项为 `{{rpc}}`，注册中心为 `{{registry}}`。启用 Dubbo 时由 bootstrap 发布 Facade；Nacos 地址使用 `DUBBO_REGISTRY_ADDRESS`，服务端口使用 `DUBBO_PORT`。

## 验证和启动

```bash
./gradlew check
./gradlew :bootstrap:bootRun
```

DDD 约定插件检查七模块依赖方向、共享内核纯净性、domain 禁止 Spring、app 查询仓储接口及 infra 的应用类型后缀。
infra 引用 app 类型只允许 QueryRepository、Query、DTO 后缀（大小写敏感）。
DDD 约定插件提供 `verifyDddDependencies` 与 `verifyDddTypes`，并统一接入 `check`。
可选事务审查 `wuli3.ddd.transaction-review.enabled=true` 仅允许 app.service 和 infra.persistence 中以 RepositoryImpl 结尾的仓储实现声明 Spring @Transactional，默认关闭。
事务包、基础设施实现类后缀以及 infra 引用 app 类型的后缀白名单可通过 `gradle.properties` 扩展；未配置时保持默认规则。
选择 MySQL 时生成 Mapper、持久化配置与扫描；运行时连接信息由环境提供，启动测试使用替身隔离数据库。

模板 2.0.0 调整了入口、DTO、Service 和仓储包路径；只生成新项目，不自动迁移已有工程。

外部服务 API 只能在 infra 声明依赖并引用；通过 domain.gateway 本地契约向 app 提供能力，不生成 app.acl。
在 gradle.properties 中以 `wuli3.ddd.external-api.coordinates` 登记精确 group:artifact，
以 `wuli3.ddd.external-api.packages` 登记外部 API 完整包前缀（均为逗号分隔），启用对应依赖与字节码门禁。
未登记的外部 API 无法自动识别；本服务自己的 api 模块不属于外部 API。

生成代码优先使用 Lombok：Spring Bean 用 `@RequiredArgsConstructor` 注入 final 依赖，聚合用 `@Getter` 和 `@Accessors(fluent = true)` 保留 `id()`、`status()` 访问方式。
DTO 和值对象保留 record；领域校验构造器及业务行为显式实现，不用 `@Data` 或公开 setter 绕过不变量。
Lombok 由 Java 约定插件提供编译期依赖和注解处理器，生成工程不可关闭 `wuli3.conventions.lombok.enabled`。
