# wuli3-redis-spring-boot-starter 使用指南

该模块在 Spring Data Redis 和 Redisson 之上提供统一 `RedisSupport` 入口、String/JSON/Hash/Set 操作以及语义明确的分布式锁执行器。模块不创建连接工厂或客户端，连接地址、认证、连接池和 Redisson 拓扑仍由应用配置。

## 引入

```kotlin
dependencies {
    implementation("com.kjs.wuli3:wuli3-redis-spring-boot-starter")
}
```

模块面向 Spring Boot 3.5.x，当前管理 Redisson 3.52.0，避免引入 Spring Boot 4、Spring Data Redis 4 或 Netty 4.2 依赖线。

## 配置

```yaml
spring:
  application:
    name: order-service
  data:
    redis:
      host: localhost
      port: 6379
      database: 0

wuli3:
  redis:
    operations:
      enabled: true
    lock:
      enabled: true
```

`RedisSupport` 使用公共服务标识作为 key 前缀：优先取 `application.service.service-code`，未配置或为空白时取 `spring.application.name`，两者均为空时启动失败。标识最多 64 个字母、数字、点、下划线或连字符。

数据结构操作和锁默认开启。存在 `RedisConnectionFactory` 时创建 `RedisSupport` Bean，存在 `RedissonClient` 时创建 `RedisLockExecutor` Bean。应用可以关闭功能，也可以声明同类型 Bean 替换默认实现。

## 序列化策略

`RedisCodec` 划分了两条线：

- **值线**：`encode`/`decode` 定义值与字符串之间的格式，写入和读取都经过这两个方法。值统一按 UTF-8 字符串传输，因此接口不提供值序列化器，同一条 schema 不会出现"序列化器 + 手工编解码"两套实现。
- **名字线**：`keySerializer(RedisKeyPrefix)` 返回携带服务前缀的 key 序列化器，`hashKeySerializer()` 返回不带前缀的 field 序列化器。`RedisSupport` 按连接工厂、`RedisCodec` 和服务前缀创建自己的 `RedisTemplate`，直接安装这两个序列化器，不读取应用声明的 `StringRedisTemplate` 或 `RedisTemplate` 的配置。

默认注册 `JsonRedisCodec`，值使用 `wuli3-json` 的标准 `Jsons`，两个序列化器都是 UTF-8 字符串序列化器。应用可以声明自己的 `RedisCodec` Bean 替换它，需要实现 `encode`、两个 `decode`、`keySerializer`、`hashKeySerializer`：

```java
@Bean
RedisCodec redisCodec() {
    return new ApplicationRedisCodec(); // 应用自定义实现
}
```

- 值的 `encode` 结果是直接写入 Redis 的字符串，`decode` 按调用方给出的类型还原；`decode` 的类型参数是必需的，因为模板序列化器无法承载目标类型。
- `keySerializer` 由实现负责把传入的服务前缀叠加到 key 上；选择自定义 Codec 意味着前缀的叠加方式也由实现决定。
- 当前模板是 `RedisTemplate<String, String>`，`decode` 也是字符串入口；二进制值格式需要同时改造 `encode`/`decode` 的载荷类型和 ops 读路径。

## 服务级 key 前缀

`RedisSupport` 读写的 key 全部带公共服务标识前缀，用于隔离共用同一 Redis 实例的多个服务，不可关闭。

前缀由 Codec 的 key 序列化器携带：`JsonRedisCodec` 用 `RedisKeyPrefix.KeySerializer` 在字符串序列化器之上叠加前缀，只改写 key，不改变字节编码方式。

- 前缀优先取 `application.service.service-code`，为空白时取 `spring.application.name`，末尾的 `:` 会自动补齐，因此 `RedisKey.persistent("orders:1")` 实际写入 `order-service:orders:1`。
- 只改写 key。Hash field、Set member 和写入的 value 不受影响。
- 读取按库中完整 key 匹配，修改服务名后旧 key 不会被新前缀读到。
- 覆盖范围：四组 operation、`delete`/`exists`/`expire`、以及用 `new RedisMinuteIdGenerator(redisSupport, namespace)` 构造的 ID 生成器，其 counter key 形如 `order-service:wuli3:id:{order}:<epochMinute>`。
- 不覆盖：应用自行注入的 `StringRedisTemplate` 或 `RedisTemplate` 写的 key（它们各自保留原配置；`RedisSupport` 只是复用同一个 `RedisConnectionFactory` 并持有自己的模板）、`RedisLockExecutor` 的锁 key、Spring Cache（用 `spring.cache.redis.key-prefix`）和 Spring Session（用 `spring.session.redis.namespace`）。

`RedisSupport` 只接受 `(RedisConnectionFactory, RedisCodec, RedisKeyPrefix)` 构造，前缀是必填参数，不存在不加前缀的构造方式。

升级注意：此前前缀固定取应用名；若已配置不同的 service-code，升级后将使用新的前缀，旧 key 不会自动迁移。需要保留原数据路径时，将 service-code 与原应用名保持一致，或先迁移数据。

## 统一入口

`RedisSupport` 聚合不同数据结构的操作对象，并集中承载整 key 的删除、批量删除、存在判断和续期。

```java
redisSupport.stringOperations().set(
        RedisKey.expiring("session:" + sessionId, Duration.ofMinutes(30)), token);

final Optional<Order> order = redisSupport.objectOperations().get(
        RedisKey.persistent("orders:" + orderId), Order.class);

redisSupport.delete(RedisKey.persistent("orders:" + orderId));
```

- `stringOperations()`：字符串读写、`setIfAbsent` 和整数自增。
- `objectOperations()`：结构化对象读写，默认使用 JSON Codec。
- `hashOperations()`：Redis Hash 字段级操作。
- `setOperations()`：Redis Set 成员级操作。
- `delete`、`exists`、`expire`：与数据结构无关的整 key 操作。

各 operation 由 `RedisSupport` 在构造时创建，不会作为独立 Bean 注册到 Spring 容器。业务统一注入 `RedisSupport`，再通过对应访问方法取得操作对象。

## JSON 对象

`ObjectRedisOperations` 默认使用 `RedisCodec` 的 `JsonRedisCodec` 实现，将值保存为 UTF-8 JSON 字符串。JSON 中不写入 Java 类名，读取时必须显式提供目标类型。

如需切换结构化值格式，可以按「序列化策略」一节提供自定义 `RedisCodec`。
`RedisCodec` 的编码结果是写入 Redis 的字符串，二进制格式需要由自定义实现自行转换为可保存的字符串。
更换 Codec 会改变 Redis value 的数据协议，不会自动兼容已有 key；生产切换前应使用新命名空间、双读迁移或提供兼容解码策略。

读取到 Redis 中的 JSON 顶层值 `null` 时，`get` 返回 `Optional.empty()`；Hash 的 `entries` 和 Set 的 `members`
会忽略反序列化为 `null` 的成员。写入 API 仍拒绝 Java `null`，以避免把“缺失值”和“显式空值”混为一谈。

```java
final Optional<List<Order>> orders = redisSupport.objectOperations().get(
        RedisKey.persistent("orders:pending"),
        new TypeReference<List<Order>>() {});
```

Java `Map` 或 `Set` 如果需要整体覆盖和整体读取，可以直接作为一个 JSON 对象通过 `objectOperations()` 保存。该方式不能原子修改单个元素。

## Redis Hash 与 Set

需要字段级或成员级修改时，应使用 Redis 原生 Hash/Set 操作：

```java
final RedisKey orderHash = RedisKey.expiring("orders", Duration.ofHours(1));
redisSupport.hashOperations().put(orderHash, orderId, order);
final Optional<Order> value = redisSupport.hashOperations().get(orderHash, orderId, Order.class);

final RedisKey pendingIds = RedisKey.persistent("orders:pending:ids");
redisSupport.setOperations().add(pendingIds, orderId);
final Set<String> ids = redisSupport.setOperations().members(pendingIds, String.class);
```

Hash field 和 Set member 的值使用 `RedisCodec` 编码，默认是普通 JSON。Hash 字段删除与 Set 成员删除属于数据结构内部操作；删除整个 Hash/Set key 应调用 `RedisSupport.delete`。

带 TTL 的 Hash/Set 在成功增加或更新成员后刷新整个 key 的过期时间。成员写入与刷新 TTL 是两个 Redis 命令，不提供跨命令原子性；需要严格原子语义的业务应使用专用 Lua 脚本。

## Redis 分钟 ID

`RedisMinuteIdGenerator` 实现 `wuli3-core` 的 `IdGenerator<Long>`，使用“UTC 分钟时间片 + Redis 分钟内序列”生成趋势递增 ID。生成器不会自动注册为 Bean，因为不同业务需要独立的 namespace 和序列空间。

```java
@Bean
IdGenerator<Long> orderIdGenerator(final RedisSupport redisSupport) {
    return new RedisMinuteIdGenerator(redisSupport, "order");
}
```

默认使用 22 位分钟内序列，每分钟最多生成 4,194,303 个 ID。Redis counter key 形如 `order-service:wuli3:id:{order}:<epochMinute>`，其中 `order-service` 是 `RedisSupport` 添加的服务前缀，默认保留 24 小时。Lua 脚本原子执行 `INCR`，并在 key 没有过期时间时设置 TTL；TTL 不会在每次分配时刷新。

传入 `RedisTemplate` 的构造器使用调用方提供的模板，不添加服务前缀。

不同分钟使用不同 ID 高位和不同 Redis key。历史 key 被删除不会影响当前分钟；当前分钟 key 被删除后序列可能从 1 重新开始，因此重复风险被限制在该分钟内，但没有被完全消除。生成器还会拒绝当前实例内检测到的时钟回拨，并在分钟内序列耗尽时抛出 `RedisIdGenerationException`。

需要调整容量或历史 key 保留时间时，可以使用完整构造器传入 `Clock`、counter TTL 和 sequence bits。counter TTL 不得小于 2 分钟，生产环境建议保持默认 24 小时或更长，以覆盖时钟偏差和短期回拨。

## 分布式锁

默认优先使用 Redisson watchdog，业务只有在能确定执行上限时才应选择固定租约。

```java
final RedisLockRequest request = RedisLockRequest.watchdog(
        "orders:submit:" + orderId, Duration.ofSeconds(2));

final OrderResult result = redisLockExecutor.execute(request, () -> submit(orderId));
```

- `tryExecute` 在竞争失败时返回 `false` 或 `Optional.empty()`。
- `execute` 在等待超时后抛出 `RedisLockAcquisitionException`。
- 等待线程被中断时会恢复中断标记并抛出 `RedisLockInterruptedException`。
- 业务异常原样传播；仅当前线程仍持锁时执行解锁，解锁失败不会覆盖原业务异常。
- `fixedLease` 不启用 watchdog，租约到期后其他节点可以获得锁。

## 使用边界

- Redis ID 生成器只提供分钟时间片算法，不提供业务 ID 格式化或业务 key 模板。
- 不修改 Spring Cache、`CacheManager` 或全局 Jackson `ObjectMapper`。
- 不创建或替换 `RedisConnectionFactory`、应用声明的 `StringRedisTemplate`/`RedisTemplate` 和 `RedissonClient`；`RedisSupport` 只创建一个自己持有的 `RedisTemplate`，不作为 Bean 注册。
- 不支持二进制值格式：模板类型和 `decode` 都是字符串入口，需要连同 ops 读路径一起改造。
- 不提供连接失败降级、重试、熔断或业务异常转换。
- 当前测试不启动真实 Redis；集群、故障转移和网络异常仍需业务集成测试覆盖。

## 验证

```bash
./gradlew :wuli3-redis-spring-boot-starter:check
./gradlew :wuli3-redis-spring-boot-starter:dependencyInsight \
  --dependency spring-data-redis --configuration runtimeClasspath
./gradlew :wuli3-redis-spring-boot-starter:dependencyInsight \
  --dependency netty-common --configuration runtimeClasspath
```
