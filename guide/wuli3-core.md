# wuli3-core 使用指南

## 1. 模块定位

`wuli3-core` 是不依赖 Spring 的基础能力模块，面向业务工程、starter 和基础设施模块复用。当前能力包括：

- 按职责拆分的错误码模型与错误策略：`com.kjs.wuli3.core.error.*`
- 断言工具：`com.kjs.wuli3.core.assertion`
- ID 生成抽象：`com.kjs.wuli3.core.id`
- 时间工具与半开区间：`com.kjs.wuli3.core.time`
- Stream 与 `BigDecimal` 聚合工具：`com.kjs.wuli3.core.stream`

使用原则：

- 公共错误应使用 `ErrorCode` 和 `ErrorCodeException` 表达，避免直接向上抛散乱的运行时异常。
- 时间区间统一使用半开区间 `[startInclusive, endExclusive)`，避免边界重复计算。
- 时间类型优先使用 `java.time`，不要引入 `java.util.Date`、`Calendar` 或 `java.sql.Date/Time/Timestamp`。
- 模块已使用 JSpecify 表达空安全语义，调用方应按 `@Nullable` 和 `@NullMarked` 约束处理空值。

## 2. 引入方式

Gradle 多模块工程中直接依赖：

```kotlin
dependencies {
    implementation(project(":wuli3-core"))
}
```

外部项目通常先通过 `wuli3-dependencies` BOM 统一版本，再声明 `wuli3-core` 依赖。发布本地 BOM：

```bash
./gradlew :wuli3-dependencies:publishToMavenLocal
```

Maven 依赖示例：

```xml
<dependency>
  <groupId>com.kjs.wuli3</groupId>
  <artifactId>wuli3-core</artifactId>
  <version>0.1.3-SNAPSHOT</version>
</dependency>
```

## 3. 错误模型

### 3.1 包职责

错误声明、传播值与协议适配分开组织：

| 包 | 主要类型 | 职责 |
| --- | --- | --- |
| `error` | `ErrorCodeException` | 携带本地错误声明或传播值的统一异常。 |
| `error.model` | `ErrorCode`、`ErrorMetadata`、`ErrorModule`、`ErrorOrigin`、`ErrorSeverity`、`ErrorVisibility` | 基础错误模型与声明式元数据。 |
| `error.builtin` | `CommonErrors`、`SystemErrors`、`ErrorFrameworkErrors` | 框架内置错误码。 |
| `error.propagation` | `ErrorCodeCarrier`、`ErrorCodePropagator` | 服务间错误传播值与字段读写。 |
| `error.resolver` | `ErrorResolver`、`ErrorMetadataResolver` | 读取元数据，分别生成完整内部传播值和公开边界投影。 |

`ErrorResolver` 合并原 `ErrorCodeResolver`、`DefaultErrorCodeResolver` 和 `ErrorCodeCarrierCodec` 的职责，不再保留这些旧类型。`resolvePropagation(ErrorCodeException)` 用于可信内部传播，不脱敏；`resolveBoundary(ErrorCodeException)` 用于最终公开边界投影；`resolveCode(ErrorCode)` 仅格式化错误标识。

责任方、严重程度和可见性的默认值通过 `@ErrorMetadata` 声明。开发者可用 `withOrigin(...)` 和 `withVisibility(...)` 分别覆盖当前异常的责任与可见性；二者独立，`CALLER + INTERNAL` 和 `SERVER + PUBLIC` 都合法。结构化明细与 HTTP 状态由 Web 边界处理。

原 `SystemErrors.ILLEGAL_ARGUMENT`、`SystemErrors.ILLEGAL_STATE` 和 `SystemErrors.UNSUPPORTED_OPERATION` 已迁移为 `CommonErrors` 中的同名常量；`SystemErrors` 现在只保留系统级错误。

核心内置错误按责任来源区分：

- `CommonErrors`：非法参数、非法状态、不支持的操作等调用方可以修正的错误，默认来源为 `CALLER`。
- `SystemErrors`：内部错误、运行配置缺失、未实现功能等需要服务自身修复的错误，默认来源为 `SERVER`。
- 数据不存在通常属于业务领域语义，应定义为 `OrderErrors.ORDER_NOT_FOUND`、`UserErrors.USER_NOT_FOUND` 等业务错误码，不使用无语义的通用错误码。

### 3.2 定义业务错误码

业务错误码应定义为枚举，并实现 `ErrorCode`。错误码枚举必须标注 `@ErrorModule`，否则解析错误元数据时会抛出框架错误。

```java
import com.kjs.wuli3.core.error.ErrorCode;
import com.kjs.wuli3.core.error.ErrorModule;
import com.kjs.wuli3.core.error.ErrorMetadata;
import com.kjs.wuli3.core.error.ErrorOrigin;
import com.kjs.wuli3.core.error.ErrorSeverity;
import com.kjs.wuli3.core.error.ErrorVisibility;

@Getter
@RequiredArgsConstructor
@ErrorModule(value = "ORDER", metadata = @ErrorMetadata(origin = ErrorOrigin.CALLER, severity = ErrorSeverity.NORMAL))
public enum OrderErrors implements ErrorCode {
    ORDER_NOT_FOUND("订单不存在"),

    @ErrorMetadata(severity = ErrorSeverity.WARNING)
    ORDER_STATUS_INVALID("订单状态不允许当前操作"),

    @ErrorMetadata(severity = ErrorSeverity.CRITICAL, origin = ErrorOrigin.SERVER)
    INVENTORY_SERVICE_UNAVAILABLE("库存服务不可用");

    private final String message;
}
```

约定：

- `@ErrorModule.value` 表示错误所属模块，应使用稳定、可读的模块名。
- `@ErrorModule.metadata` 是模块默认元数据；枚举常量上的 `@ErrorMetadata` 会覆盖对应字段。
- 本地枚举的常量名和声明类型只由元数据解析器在解析枚举时读取；传播错误直接携带完整字符串错误码。

### 3.3 抛出错误

使用 `ErrorCodeException` 携带错误码和消息。

```java
import com.kjs.wuli3.core.error.ErrorCodeException;

throw new ErrorCodeException(OrderErrors.ORDER_NOT_FOUND);
```

自定义消息：

```java
throw new ErrorCodeException(OrderErrors.ORDER_STATUS_INVALID, "订单已完成，不能取消");
```

仅当同一错误码在个别抛出点需要不同输出策略时，才使用运行时覆盖：

```java
throw new ErrorCodeException(OrderErrors.ORDER_STATUS_INVALID)
        .withVisibility(ErrorVisibility.MESSAGE_ONLY);
```

如果下游的调用方错误来自本服务构造了错误请求，可在业务适配层重新确定责任：

```java
throw remoteException.withOrigin(ErrorOrigin.SERVER)
        .withVisibility(ErrorVisibility.INTERNAL);
```

责任和可见性分别求值，未覆盖的维度继续继承。优先级为：适配层显式映射（如 Web 的 `WebErrorMapper`）> 当前异常运行时覆盖 > 远端协议元数据或本地声明。Core 不推断下游错误是否由最终用户导致。

### 3.4 责任来源、严重程度与可见性

`ErrorOrigin` 用于表达由谁修正错误，是 Web 适配层确定默认 HTTP 状态的唯一业务语义：

- `CALLER`：调用方通过修正请求、状态或所选能力可以解决，默认映射为 4xx。
- `SERVER`：服务自身或其依赖需要修复，默认映射为 5xx。

`ErrorSeverity` 用于表达告警和处置优先级，不再决定 HTTP 状态：

- `NORMAL`：常规业务或输入错误。
- `WARNING`：影响局限于当前请求或局部业务，但需要关注。
- `CRITICAL`：服务功能受影响，需要及时处理。
- `FATAL`：服务不可恢复或可能造成严重后果，需要立即处理。

`ErrorVisibility` 用于控制错误对外输出边界：

- `PUBLIC`：错误信息可以对外输出。
- `CODE_ONLY`：只输出错误码。
- `MESSAGE_ONLY`：只输出消息。
- `INTERNAL`：隐藏具体错误码和消息，不表示责任一定属于服务方。

可见性只限制最终对外展示，不限制可信内部 RPC 传输。严重程度用于诊断和告警，不作为改写传播内容的依据。

例如，JSON 序列化失败、消息发送失败等基础设施错误应声明为 `SERVER`；订单不存在、参数不合法等调用方可修正的业务错误保持默认的 `CALLER` 即可。

### 3.5 解析错误元数据

`ErrorMetadataResolver` 会缓存错误模块和固有元数据：

```java
import com.kjs.wuli3.core.error.resolver.ErrorMetadataResolver;
final ErrorOrigin origin = ErrorMetadataResolver.instance().getOrigin(OrderErrors.ORDER_STATUS_INVALID);
final ErrorSeverity severity = ErrorMetadataResolver.instance().getSeverity(OrderErrors.ORDER_STATUS_INVALID);
```

正常业务代码通常不需要直接调用解析器，适配层或响应转换层可以使用它读取策略。

### 3.6 本地声明与错误传播

- `ErrorCode` 是统一错误标识契约，本地通常由带 `@ErrorModule` 的业务枚举实现。
- `ErrorCodeException` 持有本地枚举或远程 `ErrorCodeCarrier`。
- `ErrorCodeCarrier` 包含 `originalCode`、`code`、`message`、`origin`、`severity`、`sourceService` 和 `visibility`。内部传播保留完整错误信息与策略，不得将整个对象作为公开 HTTP 响应。

```java
final ErrorResolver resolver = new ErrorResolver("order");
// 可信内部传播不提前脱敏。
final ErrorCodeCarrier protocol = resolver.resolvePropagation(exception);
final ErrorCodeException restored = new ErrorCodeException(protocol);
// 公开出口才应用策略，只输出投影后的展示字段。
final ErrorCodeCarrier visible = resolver.resolveBoundary(restored);
```

Web 默认输出如下。占位码添加当前出口服务的非空服务前缀；HTTP 状态属于 Web 默认策略，开发者可覆盖。

| 责任 | 可见性 | 展示错误码 | 展示消息 | 默认 HTTP 状态 |
| --- | --- | --- | --- | --- |
| `CALLER` | `PUBLIC` | 当前错误码 | 当前消息 | 400 |
| `CALLER` | `CODE_ONLY` | 当前错误码 | 请求未被接受 | 400 |
| `CALLER` | `MESSAGE_ONLY` | `COMMON.REQUEST_REJECTED` | 当前消息 | 400 |
| `CALLER` | `INTERNAL` | `COMMON.REQUEST_REJECTED` | 请求未被接受 | 400 |
| `SERVER` | `PUBLIC` | 当前错误码 | 当前消息 | 500 |
| `SERVER` | `CODE_ONLY` | 当前错误码 | 内部错误 | 500 |
| `SERVER` | `MESSAGE_ONLY` | `SYSTEM.INTERNAL_ERROR` | 当前消息 | 500 |
| `SERVER` | `INTERNAL` | `SYSTEM.INTERNAL_ERROR` | 内部错误 | 500 |

Dubbo 对上述八种组合均保留当前错误码、消息和元数据，不应用占位策略，也不携带 HTTP 状态。相比旧行为，隐藏调用方错误不再生成 `SYSTEM.INTERNAL_ERROR`，`CODE_ONLY` 调用方错误的占位消息也由“内部错误”改为“请求未被接受”。

本地错误的 `originalCode` 是完整的 `SERVICE.MODULE.ERROR_NAME`。远程错误再次传播时保留最初的 `originalCode/sourceService`，继承责任、严重程度和可见性；本地运行时覆盖优先。公开投影隐藏码时使用当前服务的占位码，但原始诊断字段仍留在 carrier 内，不能整体序列化对外。

`ErrorCodePropagator` 只负责内部服务间协议字段，不承担可见性策略：

```java
final ErrorCodePropagator propagator = new ErrorCodePropagator();
propagator.inject(protocol, fieldWriter);
final Optional<ErrorCodeCarrier> decoded = propagator.extract(fieldReader);
```

内部传播字段为 `X-Wuli3-Error-Original-Code`、`X-Wuli3-Error-Code`、`X-Wuli3-Error-Message`、`X-Wuli3-Error-Origin`、`X-Wuli3-Error-Severity`、`X-Wuli3-Error-Source-Service` 和新增的 `X-Wuli3-Error-Visibility`。缺少必需字段或枚举值非法时拒绝解码，Dubbo 消费方保留原异常；来源服务缺失时使用空字符串。

兼容性与升级顺序：

- 旧六参数 carrier 构造器继续可用，默认 `PUBLIC`；协议缺少 `visibility` 同样按 `PUBLIC` 接收旧版已投影内容。不兼容缺少必需 `originalCode` 的更早协议。
- 新版不会通过 `originalCode` 恢复旧版已隐藏的展示码，也无法恢复旧版丢弃的消息。
- **先升级消费端及 Web 出口，再升级提供端**。旧消费端不识别可见性，可能将新提供端传来的完整敏感信息当成公开内容。多跳调用应从最终出口向上游逐层升级，确保接收及转发节点保留策略，公开出口执行过滤。
- 旧提供端向新消费端传播时保留旧版脱敏结果，但无法获得完整诊断信息；全部升级后才形成完整的新传播链。

Web 在 Core 模型上执行异常识别、投影及 HTTP 状态映射，两种响应格式只输出允许展示的字段。结构化校验详情仅在 `PUBLIC` 时输出。普通 `IllegalArgumentException` 默认归类为参数错误；框架错误按已知协议语义分类，未知异常才兜底为内部错误。开发者可覆盖默认分类。

Dubbo 对普通异常保留原生处理机制，不再统一包装为内部错误；具体异常类型能否保留取决于 Dubbo 原生规则。Core 不全局转换 JDK 异常。可预期的业务失败应显式抛出 `ErrorCodeException`；基础设施失败应在对应适配器中表达明确的错误语义。

这种拆分保证服务间不需要共享所有业务错误枚举。例如提供方的 `GroupErrors.PERMISSION_DENIED` 可以映射为 `GROUP.PERMISSION_DENIED` 后传播，消费方将其作为 `ErrorCodeException` 携带的 `ErrorCodeCarrier` 接收，不需要把 `GroupErrors` 放进自己的 classpath。

## 4. 断言工具

`Asserts` 使用延迟抛异常的链式风格。断言方法返回 `AssertCondition`，当条件不满足时调用 `throwException(...)` 抛出 `ErrorCodeException`。

```java
Asserts.notBlank(name).throwException(CommonErrors.ILLEGAL_ARGUMENT, "name 不能为空");
Asserts.notNull(order).throwException(OrderErrors.ORDER_NOT_FOUND);
Asserts.isTrue(amount.signum() > 0).throwException(CommonErrors.ILLEGAL_ARGUMENT, "amount 必须大于 0");
```

当前可用断言：

| 方法 | 通过条件 | 失败条件 |
| --- | --- | --- |
| `isTrue(boolean condition)` | `condition == true` | `condition == false` |
| `notNull(Object obj)` | `obj != null` | `obj == null` |
| `notBlank(String value)` | 非 `null` 且 `!value.isBlank()` | `null` 或空白字符串 |
| `notEmpty(String value)` | 非 `null` 且 `!value.isEmpty()` | `null` 或空字符串 |
| `isEmptyCollection(Collection<?> collection)` | `collection == null` 或集合为空 | 集合非空 |
| `isNotEmptyCollection(Collection<?> collection)` | 集合非 `null` 且非空 | `null` 或集合为空 |
| `isEmptyMap(Map<?, ?> map)` | `map == null` 或 Map 为空 | Map 非空 |
| `isNotEmptyMap(Map<?, ?> map)` | Map 非 `null` 且非空 | `null` 或 Map 为空 |

抛错方式：

```java
Asserts.notBlank(name).throwException(CommonErrors.ILLEGAL_ARGUMENT);
Asserts.notBlank(name).throwException(CommonErrors.ILLEGAL_ARGUMENT, "name 不能为空");
Asserts.notBlank(name).throwIllegalArgumentException("name 不能为空");
```

反转断言：

```java
Asserts.isTrue(deleted).reversed().throwException(CommonErrors.ILLEGAL_STATE, "记录已删除");
```

注意：

- `Asserts` 当前不返回被校验对象，只负责延迟抛异常。
- `throwIllegalArgumentException(String message)` 会使用 `CommonErrors.ILLEGAL_ARGUMENT`。
- 对集合和 Map 的空判断中，`isEmptyCollection(null)` 与 `isEmptyMap(null)` 视为通过。

## 5. ID 生成

`IdGenerator<T>` 是 ID 生成抽象：

```java
public interface IdGenerator<T> {
    T nextId();
}
```

默认提供 UUID 字符串生成器：

```java
final String id = UuidStringIdGenerator.INSTANCE.nextId();
```

业务需要雪花 ID、数据库号段或外部 ID 服务时，应实现 `IdGenerator<T>`，不要把具体算法散落在业务代码中。

```java
public final class OrderIdGenerator implements IdGenerator<String> {

    @Override
    public String nextId() {
        return "ORDER-" + UuidStringIdGenerator.INSTANCE.nextId();
    }
}
```

## 6. 时间工具

### 6.1 ClockProvider

`ClockProvider` 用于注入应用时钟，避免业务代码硬编码系统时间。

生产环境：

```java
final ClockProvider clockProvider = ClockProvider.system(ZoneId.of("Asia/Shanghai"));
final Instant now = clockProvider.instant();
```

测试环境：

```java
final ClockProvider fixedClock = ClockProvider.fixed(
        Instant.parse("2026-07-08T00:00:00Z"),
        ZoneOffset.UTC);
```

### 6.2 DateRange

`DateRange` 表示本地日期半开区间 `[startInclusive, endExclusive)`。

```java
final DateRange range = new DateRange(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 3));

range.contains(LocalDate.of(2026, 7, 1)); // true
range.contains(LocalDate.of(2026, 7, 3)); // false
range.days(); // 2
```

如业务输入是闭区间，可以使用工厂方法：

```java
final DateRange closed = DateRange.closed(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 3));

closed.endExclusive(); // 2026-07-04
```

区间能力：

```java
range.isEmpty();
range.overlaps(otherRange);
range.intersection(otherRange);
```

### 6.3 TimeRange

`TimeRange` 表示 `Instant` 半开区间 `[startInclusive, endExclusive)`。

```java
final TimeRange range = new TimeRange(
        Instant.parse("2026-07-08T00:00:00Z"),
        Instant.parse("2026-07-08T01:00:00Z"));

range.contains(Instant.parse("2026-07-08T00:30:00Z")); // true
range.contains(Instant.parse("2026-07-08T01:00:00Z")); // false
range.duration(); // PT1H
```

### 6.4 DateTimeFormats

统一格式常量：

| 常量 | Pattern |
| --- | --- |
| `DateTimeFormats.DATE` | `yyyy-MM-dd` |
| `DateTimeFormats.TIME` | `HH:mm:ss` |
| `DateTimeFormats.DATE_TIME` | `yyyy-MM-dd HH:mm:ss` |
| `DateTimeFormats.COMPACT_DATE` | `yyyyMMdd` |

示例：

```java
final String value = LocalDateTime.now().format(DateTimeFormats.DATE_TIME);
```

## 7. Stream 工具

### 7.1 BigDecimalCollectors

求和：

```java
final BigDecimal total = orders.stream()
        .collect(BigDecimalCollectors.summing(Order::amount));
```

统计：

```java
final BigDecimalSummary summary = orders.stream()
        .collect(BigDecimalCollectors.summarizing(Order::amount));

summary.count();
summary.sum();
summary.min();
summary.max();
summary.average(2, RoundingMode.HALF_UP);
```

规则：

- `mapper` 返回 `null` 时会被忽略。
- 空流的 `sum()` 为 `BigDecimal.ZERO`。
- 空流的 `min()`、`max()`、`average(...)` 返回 `Optional.empty()`。

### 7.2 MoreCollectors 与 MapMerger

按插入顺序收集为 `LinkedHashMap`：

```java
final Map<String, Order> orderMap = orders.stream()
        .collect(MoreCollectors.toLinkedMap(Order::id, Function.identity()));
```

默认遇到重复 key 会抛出 `ErrorCodeException(CommonErrors.ILLEGAL_STATE)`。如需要自定义合并策略：

```java
final Map<String, Order> orderMap = orders.stream()
        .collect(MoreCollectors.toLinkedMap(
                Order::id,
                Function.identity(),
                MapMerger::keepLastValue));
```

可用合并策略：

- `MapMerger.keepFirstValue(left, right)`：保留旧值。
- `MapMerger.keepLastValue(left, right)`：使用新值。
- `MapMerger.keepFirstNonNullValue(left, right)`：优先保留第一个非空值。
- `MapMerger.throwDuplicate(left, right)`：重复时抛错。

### 7.3 StreamUtils

过滤空值：

```java
final List<String> values = StreamUtils.filterNotNull(source.stream())
        .toList();
```

按 key 去重：

```java
final List<Order> distinctOrders = orders.stream()
        .filter(StreamUtils.distinctBy(Order::id))
        .toList();
```

注意：

- `distinctBy(...)` 内部维护并发 Set，适合单次流处理；不要复用同一个 Predicate 处理多个独立业务批次。
- `distinctBy(...)` 支持 `null` key，并将第一个 `null` key 视为有效唯一值。

## 8. 测试与质量门禁

开发 `wuli3-core` 后建议执行：

```bash
./gradlew :wuli3-core:test
./gradlew :wuli3-core:check
```

`check` 会执行编译、测试、Checkstyle、Spotless、Forbidden APIs、Error Prone、NullAway、SpotBugs 和 JaCoCo 等质量检查。提交前如涉及跨模块行为，建议运行：

```bash
./gradlew clean check
```

## 9. 常见问题

### 9.1 为什么区间使用半开区间？

半开区间 `[startInclusive, endExclusive)` 可以避免相邻区间边界重复。例如 `[2026-07-01, 2026-07-03)` 与 `[2026-07-03, 2026-07-05)` 不重叠，适合按天、按小时分片统计。

### 9.2 什么时候使用 `CommonErrors` 和 `SystemErrors`？

`CommonErrors` 用于跨模块复用的调用方错误，例如非法参数、非法状态和不支持的操作；`SystemErrors` 用于内部错误、运行配置缺失和未实现功能等系统错误。数据不存在应优先使用带领域语义的错误码，例如 `OrderErrors.ORDER_NOT_FOUND`。业务语义明确的错误都应定义自己的 `ErrorCode` 枚举，避免所有业务错误都落到通用错误中。

### 9.3 如何返回结构化错误明细？

`ErrorCodeException` 不承载任意明细对象。参数校验等由 Web starter 已知的框架异常会在 `ApiResponse.data` 中返回受控的 `ValidationErrorDetails`。业务需要返回其他结构化明细时，应在应用自己的 Web 边界定义响应 DTO 和异常映射规则，并明确其可见性与脱敏策略。

### 9.4 `Asserts` 为什么不直接返回被校验对象？

当前 `Asserts` 的设计是构造断言条件并在最后选择错误模型。调用方可以根据同一个条件选择错误码、自定义消息、非法参数错误或反转条件。需要返回值时，应在断言通过后继续使用原变量。
