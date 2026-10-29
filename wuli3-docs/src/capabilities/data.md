---
description: MySQL、Redis、Elasticsearch 与 MongoDB 的接入范围、数据边界和实际验收要求。
---

# 数据访问

Wuli3 让业务通过应用和领域端口表达数据需求，再由基础设施实现选择数据库、缓存或搜索。starter 提供依赖整合与明确的适配能力，数据模型、连接配置和业务事务仍由应用设计。

## 先选择数据的职责

| 能力 | 当前模块提供什么 | 应用负责什么 |
| --- | --- | --- |
| MySQL | MyBatis-Plus、分页适配、SQL 观测、全表修改防护 | 驱动、表结构、Mapper、查询、事务、迁移 |
| Redis | String/JSON/Hash/Set 操作、服务前缀、分布式锁 | 缓存一致性、命名、TTL、失效、降级 |
| Elasticsearch | Spring Data Elasticsearch 依赖聚合 | 文档模型、索引与 mapping、检索和同步 |
| MongoDB | Spring Data MongoDB 依赖聚合 | 文档边界、索引、事务条件和查询 |

不要因为同时存在四个 starter 就同时引入四种存储。先让一个完整用例可运行，再为真实查询和一致性需求增加组件。

## MySQL：保持仓储边界

聚合仓储负责保存领域状态；查询仓储负责列表、分页和只读视图。Mapper、数据库 PO 和数据库分页类型留在基础设施层，避免泄漏到领域与 API。

应用显式选择 JDBC 驱动，连接使用 `spring.datasource.*`，Mapper 等配置使用 `mybatis-plus.*`。starter 不创建专属数据源，也不执行建库和迁移。

### 分页接入

统一分页请求先经过排序白名单映射，再由 `PaginationHelper` 转成数据库分页；查询结果转换回 `PageResult`。

实际 SQL 分页需要应用注册 `PaginationInnerInterceptor`。若应用自建总拦截器链，应把分页拦截器加入该实例并放在内部链末尾。只调用分页适配工具不会自动给 SQL 加上分页。

排序白名单应是业务可查询字段到真实列名的固定映射。未知字段应在进入 SQL 前拒绝，不能把请求字段直接传给数据库排序。

### SQL 观测与防护

```yaml
wuli3:
  mysql:
    sql:
      enabled: true
      logging-enabled: true
      slow-query-enabled: true
      slow-query-threshold: 1s
      include-parameters: false
    block-attack:
      enabled: true
```

SQL 观测总开关默认关闭；打开后还需分别设置 `logging-enabled` 与 `slow-query-enabled`，两者默认也关闭。启用后普通 SQL 默认使用 DEBUG，慢 SQL 使用 WARN，执行异常使用 ERROR。参数默认不输出；显式启用后仍有敏感字段处理与长度限制，应用应检查自身业务字段是否还需保护。

`SqlAlertNotifier` 是慢 SQL 与异常 SQL 的外部通知扩展。默认全表更新、删除防护开启，但它不替代业务条件校验、租户隔离或数据权限。

## Redis：明确数据形态和生命周期

Redis 操作默认开启，并依赖已有连接工厂；锁默认开启，并依赖 Redisson 客户端。开关分别为 `wuli3.redis.operations.enabled` 与 `wuli3.redis.lock.enabled`。

使用统一操作入口必须配置非空 `spring.application.name`。它会成为 key 前缀，例如服务 `order-service` 的业务 key `orders:1` 实际写为 `order-service:orders:1`。

| 数据形态 | 适用场景 | 注意事项 |
| --- | --- | --- |
| String | 标记、计数和简单文本 | 明确数值与文本协议 |
| JSON 对象 | 整体缓存、批量读取对象 | 读取时提供目标类型；整体覆盖 |
| Hash | 按字段更新的集合 | TTL 属于整个 key |
| Set | 成员去重与集合操作 | 成员编码必须保持兼容 |

默认 JSON 值不写入 Java 类名。统一入口持有自己的模板，不改变应用原有 RedisTemplate、Spring Cache 或 Session 配置。

服务前缀只覆盖该操作入口及基于它构建的分钟 ID；不自动覆盖分布式锁 key、应用自行使用的模板、Spring Cache 或 Session。修改服务名后，原前缀的数据不会被新前缀自动读到。

### 缓存不是事实来源

应用应先确定缓存何时写入、何时失效、未命中如何回源，以及数据库更新成功而缓存操作失败时如何恢复。starter 不内置统一重试、熔断或业务降级。

写入 API 拒绝 Java 空值。读取 JSON 顶层 `null` 时返回空结果；如果业务需要区分“没有 key”和“已缓存空查询”，应设计明确的缓存载荷或标记。

带 TTL 的 Hash/Set 成员更新会刷新整个 key 的过期时间。成员写入与刷新 TTL 是两个 Redis 命令，严格原子需求应采用专用脚本或另行设计存储方式。

替换 `RedisCodec` 会改变数据协议。需要迁移已有数据时，先选择新命名空间、兼容解码或双读方案，不能仅替换 Bean 就假设历史值仍可读取。

### 锁与 ID

锁优先使用 watchdog 续租；固定租约仅适用于能明确约束执行时间的业务。租约到期后其他节点可能获得锁，应用仍需考虑数据库约束和幂等。

竞争失败可以选择返回空结果或抛出获取失败异常；业务异常保留原始语义。锁名不带统一操作入口的服务前缀，跨服务协调与隔离规则要由应用明确命名。

Redis 分钟 ID 使用时间片与分钟内序列，需应用按业务 namespace 显式创建。当前分钟计数 key 被删除可能导致重复，时钟回拨与容量耗尽也需处理。它不替代持久层唯一约束。

## 搜索与文档数据库

Elasticsearch 和 MongoDB 当前以依赖聚合为主，采用 Spring Data 的标准 API。没有额外的统一 Repository 基类、索引迁移器或多租户路由。

| 系统 | 主要连接配置 | 应用必须明确的部署内容 |
| --- | --- | --- |
| Elasticsearch | `spring.elasticsearch.uris` 及认证配置 | 索引 mapping、别名、重建与数据同步 |
| MongoDB | `spring.data.mongodb.uri` | 索引、文档结构、事务适用的集群条件 |

数据库与搜索索引同时更新时，应先定义哪一侧是事实来源，以及另一侧延迟或失败时如何补偿。框架不会自动把它们变成一个原子事务。

## 从单元测试走到真实验证

仓储单元测试验证映射和业务分支，自动配置测试验证 Bean 条件；它们不能证明数据库权限、连接参数、网络超时和集群行为正确。

接入后至少验证一次真实写入、读取、分页、事务回滚和连接失败。缓存需要验证 TTL 与失效，锁需要验证并发和租约边界，搜索需要验证索引不存在与同步延迟。

仓库的独立集成验证入口及质量门禁见[构建与验证](/engineering/build)。跨存储或跨服务的通知机制继续阅读[事件与消息](/capabilities/events)。

## 源码与详细指南

- [MySQL starter 指南](https://github.com/weiran-tech/wuli3/blob/main/guide/wuli3-mysql-spring-boot-starter.md)
- [Redis 自动配置](https://github.com/weiran-tech/wuli3/blob/main/wuli3-redis-spring-boot-starter/src/main/java/com/kjs/wuli3/redis/autoconfigure/RedisAutoConfiguration.java)
