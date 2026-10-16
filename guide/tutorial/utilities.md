# 业务开发教程：工具类、错误模型与常用数据操作

本文面向已经创建 Spring Boot 应用的开发者，介绍断言、错误码、ID、时间与集合等基础能力。示例以当前源码签名为准；代码块中的局部变量片段放在业务方法或测试方法中执行，标有文件名的示例按文件保存。

## 1. 按实际使用的能力引入模块

在项目已导入 `wuli3-dependencies` BOM 的前提下，按需声明依赖：

```kotlin
dependencies {
    implementation("com.kjs.wuli3:wuli3-core")
}
```

本篇能力不依赖 Spring。其他模块的依赖和配置放在对应实战篇，按使用场景添加。

## 2. 用 Asserts 校验公共 API 输入

`whenXxx(...)` 表达“何时失败”，末尾的 `throw...(...)` 才执行抛错：

```java
import com.kjs.wuli3.core.assertion.Asserts;

final String name = "新订单";
final int quantity = 2;
Asserts.whenBlank(name).throwIllegalArgumentException("订单名称不能为空");
Asserts.whenFalse(quantity > 0).throwIllegalArgumentException("数量必须大于 0");
```

常见对应关系：

| 输入约束 | 写法 |
| --- | --- |
| 对象不可为空 | `Asserts.whenNull(value)` |
| 字符串不可为空白 | `Asserts.whenBlank(value)` |
| 集合至少一项 | `Asserts.whenEmptyCollection(values)` |
| 条件必须成立 | `Asserts.whenFalse(condition)` |
| 条件不得成立 | `Asserts.whenTrue(condition)` |

`throwIllegalArgumentException(...)` 抛出的实际类型是 `ErrorCodeException`，错误码为 `CommonErrors.ILLEGAL_ARGUMENT`，并不是 JDK 的 `IllegalArgumentException`。断言不返回校验后的对象，也不替换对象引用；启用了空安全检查的业务项目，仍需按其静态分析规则处理可空变量。

请求、消息及公共业务 API 的输入错误走这条统一错误链。构造器依赖缺失、Bean 装配错误属于内部不变量，不要伪装成用户参数错误。

## 3. 定义业务错误码，并在失败处使用

新建 `OrderErrors.java`，无需 Lombok，也无需把枚举注册成 Spring Bean：

```java
package com.example.order;

import com.kjs.wuli3.core.error.model.ErrorCode;
import com.kjs.wuli3.core.error.model.ErrorMetadata;
import com.kjs.wuli3.core.error.model.ErrorModule;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import com.kjs.wuli3.core.error.model.ErrorSeverity;
import com.kjs.wuli3.core.error.model.ErrorVisibility;

/**
 * 声明订单业务失败及其边界输出策略。
 *
 * @author 国杨 create on 2026/10/16 10:00
 */
@ErrorModule(name = "ORDER", defaultMetadata = @ErrorMetadata(
        origin = ErrorOrigin.CALLER,
        severity = ErrorSeverity.NORMAL,
        visibility = ErrorVisibility.PUBLIC))
public enum OrderErrors implements ErrorCode {
    ORDER_NOT_FOUND("订单不存在"),
    ORDER_STATUS_INVALID("订单状态不允许当前操作"),

    @ErrorMetadata(origin = ErrorOrigin.SERVER,
            severity = ErrorSeverity.CRITICAL,
            visibility = ErrorVisibility.INTERNAL)
    INVENTORY_UNAVAILABLE("库存服务不可用");

    private final String message;

    OrderErrors(final String message) {
        this.message = message;
    }

    /** 返回该业务错误的默认消息。 */
    @Override
    public String getMessage() {
        return this.message;
    }
}
```

`@ErrorModule.name` 只能包含大写英文字母及下划线。解析器按需读取并缓存枚举元数据；没有额外的错误码扫描或注册步骤。常量上的 `@ErrorMetadata` 优先于类上的注解，再优先于 `defaultMetadata`。**选中某层注解后读取的是这份注解的全部属性（包括默认值），不是逐属性向上合并。** 因此需要覆写时建议像上例一样明确声明全部三个维度。

在同包业务代码中使用：

```java
import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.core.error.ErrorCodeException;

final boolean exists = false;
Asserts.whenFalse(exists).throwException(OrderErrors.ORDER_NOT_FOUND);
// 在另一个明确失败的业务分支，也可以直接抛出：
// throw new ErrorCodeException(OrderErrors.ORDER_STATUS_INVALID, "已完成订单不能取消");
```

责任与展示策略独立：`CALLER` 表示调用方可修正，`SERVER` 表示服务或依赖需要修复；`INTERNAL` 隐藏对外错误细节。Web starter 负责最终公开响应，内部 RPC 传播保留诊断元数据。不要将 `ErrorCodeCarrier` 整体作为 HTTP DTO 返回。

## 4. ID、时钟与区间

### 4.1 UUID 和 ID 扩展点

```java
import com.kjs.wuli3.core.id.IdGenerator;
import com.kjs.wuli3.core.id.UuidStringIdGenerator;

final IdGenerator<String> generator = UuidStringIdGenerator.INSTANCE;
final String orderId = generator.nextId();
```

应用服务可以通过构造器依赖 `IdGenerator<String>`，由应用配置提供具体实现。框架不会仅因为声明了接口就为每种业务自动创建生成器；使用数据库号段、远端 ID 服务时实现 `nextId()` 即可，但要自行保证唯一性、容量和失败语义。

### 4.2 用可替换时钟编写可测试逻辑

```java
import com.kjs.wuli3.core.time.ClockProvider;
import com.kjs.wuli3.core.time.DateTimeFormats;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

final ClockProvider productionClock = ClockProvider.system(ZoneId.of("Asia/Shanghai"));
final ClockProvider testClock = ClockProvider.fixed(
        Instant.parse("2026-10-16T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
final String formatted = LocalDateTime.now(testClock.clock()).format(DateTimeFormats.DATE_TIME);
// formatted 为 2026-10-16 10:00:00；测试不会随机器时间变化。
```

实际服务注入 `ClockProvider`，生产提供系统时钟，测试提供固定时钟。只在测试里创建固定时钟，却在服务内部继续调用 `Instant.now()`，不能实现可测试性。

```java
import com.kjs.wuli3.core.time.DateRange;
import java.time.LocalDate;

final DateRange range = DateRange.closed(
        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3));
final long days = range.days(); // 3
final LocalDate endExclusive = range.endExclusive(); // 2026-10-04
```

`DateRange` 和 `TimeRange` 统一使用 `[startInclusive, endExclusive)`；数据库过滤应对应 `>= start` 与 `< end`。`TimeRange` 的端点类型是 `Instant`，日期转时间点时先确定业务时区。

## 5. 金额聚合与集合转换

```java
import com.kjs.wuli3.core.stream.BigDecimalCollectors;
import com.kjs.wuli3.core.stream.BigDecimalSummary;
import com.kjs.wuli3.core.stream.MapMerger;
import com.kjs.wuli3.core.stream.MoreCollectors;
import com.kjs.wuli3.core.stream.StreamUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

record Line(String sku, BigDecimal amount) {}
final List<Line> lines = List.of(
        new Line("A", new BigDecimal("10.50")),
        new Line("B", new BigDecimal("20.00")));
final BigDecimal total = lines.stream().collect(BigDecimalCollectors.summing(Line::amount));
final BigDecimalSummary summary = lines.stream()
        .collect(BigDecimalCollectors.summarizing(Line::amount));
final BigDecimal average = summary.average(2, RoundingMode.HALF_UP).orElse(BigDecimal.ZERO);
final Map<String, Line> bySku = lines.stream().collect(MoreCollectors.toLinkedMap(
        Line::sku, Function.identity(), MapMerger::keepLastValue));
final List<Line> distinct = lines.stream().filter(StreamUtils.distinctBy(Line::sku)).toList();
```

金额用十进制字符串构造 `BigDecimal`。聚合器忽略 mapper 返回的 `null`，空流求和为零，空流最小值、最大值、平均值为 `Optional.empty()`。如果业务不允许缺失金额，应先校验，不能依靠“忽略空值”掩盖脏数据。

`toLinkedMap` 不传合并函数时，重复 key 抛出统一非法状态错误；传 `keepLastValue` 表示明确选择覆盖策略。`distinctBy` 有状态，只供一次流处理使用；并行流不应依赖它保留遇到的第一项。

## 6. 接入业务边界能力

基础工具可直接组合到业务方法。需要 Web、持久化或协议边界时，请继续对应实战篇，避免只复制单个调用而遗漏装配与生命周期：

| 需求 | 教程 |
| --- | --- |
| Controller、认证、校验、响应与业务补全项 | [Web 接入](web.md) |
| 业务错误码、异常映射、权限目录和授权 | [错误与权限](errors-permissions.md) |
| 资源 URL、脱敏、权限展示、实际 Mapper 测试 | [JSON 与资源](json.md) |
| MySQL 分页、Redis 缓存与锁、ES/Mongo 查询 | [数据访问](data-access.md) |
| 请求上下文、异步传播、事件发布和消费 | [上下文与事件](context-events.md) |

其中错误码定义在本篇作为最小入门保留；HTTP 输出策略和权限扩展以错误与权限篇为准。
