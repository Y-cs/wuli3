# wuli3-pagination 使用指南

`wuli3-pagination` 提供与持久化技术无关的分页查询和结果契约。它只依赖
`wuli3-core`，适合放在应用层仓储接口中，避免把 MyBatis-Plus 的分页类型泄漏到领域或应用
代码。

## 引入

```kotlin
dependencies {
    implementation("com.kjs.wuli3:wuli3-pagination")
}
```

使用 [wuli3-mysql-spring-boot-starter](wuli3-mysql-spring-boot-starter.md) 时，该模块会作为
传递依赖提供，不需要重复声明。

## 查询条件

`PageQuery` 的页码从 1 开始，默认页大小常量为 20，允许的最大页大小为 200。排序条件按
列表顺序表示优先级：

```java
final PageQuery query = new PageQuery(
        2,
        20,
        List.of(
                new Sort("createdAt", SortDirection.DESC),
                new Sort("id", SortDirection.ASC)));
```

`PageQuery` 会复制排序列表，调用方后续修改原列表不会影响查询条件。页码或页大小越界、空
排序字段和空排序方向都会通过统一断言转换为 `ErrorCodeException`。

## 查询结果

仓储接口返回 `PageResult<T>`，而不是数据库分页对象：

```java
PageResult<Order> result = orderRepository.findPage(query);
long total = result.total();
List<Order> orders = result.items();
```

结果列表不可变；`total` 表示满足条件的总记录数，`pageNumber` 和 `pageSize` 是本次查询
实际使用的分页参数。

## MyBatis-Plus 适配

MySQL starter 提供 `PaginationHelper`：

```java
final Page<OrderEntity> page = PaginationHelper.toPage(
        query, field -> switch (field) {
            case "createdAt" -> "created_at";
            case "id" -> "id";
            default -> {
                Asserts.whenFalse(false).throwIllegalArgumentException("不支持的排序字段");
                yield "";
            }
        });
final IPage<OrderEntity> selected = orderMapper.selectPage(page, wrapper);
final PageResult<OrderEntity> result =
        PaginationHelper.toPageResult(selected);
```

适配器只负责分页参数和结果的转换，不负责构造查询条件、校验业务过滤条件或开启事务。实际 SQL 分页需按 [MySQL guide](wuli3-mysql-spring-boot-starter.md) 注册分页拦截器。

分页 records 实现 `Serializable`，用于内部 RPC 契约。`PageResult<T>` 的元素也必须满足所选 RPC 序列化器要求。

### 排序安全边界

`Sort.field` 永远按应用字段解释。`fieldMapper` 是数据库列名的唯一边界，必须使用固定白
名单映射；禁止把请求中的字段直接作为列名、SQL 片段或 `ORDER BY` 字符串传入。未知字段应
在映射时拒绝并返回统一参数错误。排序方向只能是 `ASC` 或 `DESC`，由 `SortDirection` 表达，
不接受原始 SQL。

## 验证

```bash
./gradlew :wuli3-pagination:check
./gradlew :wuli3-mysql-spring-boot-starter:check
```
