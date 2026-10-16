# wuli3-mysql-spring-boot-starter 使用指南

该模块是 MyBatis-Plus Spring Boot 3 starter，并提供可选的 SQL 日志与慢 SQL 告警能力。它不创建数据源，也不负责数据库迁移。

## 引入

```kotlin
dependencies {
    implementation("com.kjs.wuli3:wuli3-mysql-spring-boot-starter")
    runtimeOnly("com.mysql:mysql-connector-j")
}
```

版本建议由 `wuli3-dependencies` BOM 管理。数据库驱动由应用根据实际数据库显式选择。

## 最小配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/app
    username: app
    password: change-me

mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml

wuli3:
  mysql:
    sql:
      enabled: true
      logging-enabled: true
      logging-level: debug
      exception-enabled: true
      slow-query-enabled: true
      slow-query-threshold: 1s
      include-parameters: false
      max-sql-length: 4096
      max-parameter-length: 256
      max-parameter-summary-length: 4096
    block-attack:
      enabled: true
```

Mapper 扫描、类型处理器和事务边界按 MyBatis-Plus/Spring Boot 的标准方式配置。模块同时传递
`wuli3-pagination`，可直接使用统一的分页查询契约；具体用法见
[wuli3-pagination 使用指南](wuli3-pagination.md)。

分页前需在应用配置中注册 `PaginationInnerInterceptor`，由 MyBatis-Plus 自动聚合；starter 不会自动启用分页：

```java
@Bean
PaginationInnerInterceptor paginationInnerInterceptor() {
    return new PaginationInnerInterceptor(DbType.MYSQL);
}
```

如果应用自行声明 `MybatisPlusInterceptor`，需将分页拦截器加入该实例并放在内部拦截器链末尾。

分页查询在仓储适配器中使用 `PaginationHelper` 完成边界转换：先将应用层的
`PageQuery` 转为 MyBatis-Plus `Page`，执行 Mapper 查询后再将 `IPage` 转为
`PageResult`。例如：

```java
final PageQuery query = new PageQuery(1, 20,
        List.of(new Sort("createdAt", SortDirection.DESC)));
final Page<OrderEntity> page = PaginationHelper.toPage(
        query, field -> switch (field) {
            case "createdAt" -> "created_at";
            default -> {
                Asserts.whenFalse(false).throwIllegalArgumentException("不支持的排序字段");
                yield "";
            }
        });
final PageResult<OrderEntity> result = PaginationHelper.toPageResult(
        orderMapper.selectPage(page, wrapper));
```

`Sort.field` 是应用层字段名，不是数据库列名或 SQL 片段。`fieldMapper` 必须使用固定的
白名单映射（例如 `Map` 或显式 `switch`）；不要直接传入用户输入、下划线转换器或字符串拼接
函数。映射不存在时应拒绝请求，避免把排序字段注入 SQL。`PageQuery` 页码从 1 开始，页大小
范围为 1 至 200；`sorts` 的顺序表示排序优先级。`PageResult` 的 `items` 为空时使用空列表，
`total` 不会为负数。

SQL 观测默认关闭。开启后，普通 SQL 默认以 `DEBUG` 记录，可通过 `logging-level: info` 改为 `INFO`；达到阈值的 SQL 以 `WARN` 记录，执行异常的 SQL 独立以 `ERROR` 记录。所有 SQL 日志固定使用带换行和 ASCII 分隔符的多行格式。慢 SQL 和异常 SQL 都会通过 `SqlAlertNotifier` 派发。观测范围包括普通查询、游标查询、更新和批执行。

参数默认不渲染；开启参数后仍会对常见密码、Token、Secret、Credential、Authorization 和 Key 字段脱敏。单个参数、整体参数摘要和 SQL 文本分别限制长度。全表更新、删除防护默认开启，可通过 `wuli3.mysql.block-attack.enabled=false` 显式关闭。

## 使用边界

- 模块传递 `mybatis-plus-spring-boot3-starter` 和 BlockAttack 所需的 `mybatis-plus-jsqlparser-4.9`，并按条件注册 SQL 观测拦截器。
- `MysqlAutoConfiguration` 仍不创建数据源、迁移器、读写分离或连接池 Bean。
- 不负责建库、迁移、连接池选择、读写分离或 SQL 审计。
- 模块测试只验证自动配置可加载，不连接真实数据库。

## 验证

```bash
./gradlew :wuli3-mysql-spring-boot-starter:check
```
