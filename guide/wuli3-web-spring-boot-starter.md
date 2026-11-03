# wuli3-web-spring-boot-starter 使用指南

该 starter 为 Spring MVC 应用提供请求上下文、统一 JSON 配置、响应包装、异常映射和扩展 SPI。

## 引入

```kotlin
dependencies {
    implementation("com.kjs.wuli3:wuli3-web-spring-boot-starter")
}
```

自动配置包含 `WebContextAutoConfiguration`、`WebJsonAutoConfiguration`、`WebErrorAutoConfiguration` 和
`WebResponseAutoConfiguration`。

## 请求上下文

默认过滤器在请求进入时创建 `InvocationContext`，写入 requestId、可信客户端 IP 和 MDC；请求结束后清理上下文。

```properties
wuli3.web.context.enabled=true
wuli3.web.context.filter-order=-2147483638
wuli3.web.context.request-id-header-name=X-Wuli3-Request-Id
wuli3.web.context.accept-external-request-id=true
wuli3.web.context.request-id-max-length=128
wuli3.web.context.trusted-proxy-cidrs=10.0.0.0/8,192.168.0.0/16
wuli3.web.context.client-ip-header-priority=X-Forwarded-For,X-Real-IP,Forwarded
```

只有直接 peer 命中 `trusted-proxy-cidrs` 时才读取转发头；列表为空时忽略所有转发头。默认的
`TrustedHttpAuthContextResolver` 根据可信内部 HTTP 请求中的 `X-Wuli3-Principal-Type` 和 `X-Wuli3-Principal-Id` 恢复
`AuthContext`。这两个字段必须完整、非空白，且主体类型必须是 `CUSTOMER`、`ADMIN` 或 `SYSTEM`；否则会整体拒绝该
认证上下文。可选的 `X-Wuli3-Principal-Permission-Token` 为空白时也会被拒绝；缺少该字段仍可建立认证上下文，但不能通过权限鉴权。
名称不参与认证上下文；页面需要显示主体名称时应从用户服务读取。
适用于统一网关完成身份认证、业务服务只恢复认证上下文的微服务架构。直接接收外部请求或使用 token、session、
安全框架 principal 等其他认证来源的应用，应提供自己的 `AuthContextResolver` Bean 替换默认实现。

### 接口登录校验

在 Controller 方法或类型上标注 `@LoginRequired`，未建立认证上下文时由 MVC 拦截器返回 401 `WEB.UNAUTHORIZED`。
未标注的接口默认允许匿名访问；接口从需要登录改为公开时删除注解即可。类型级声明作用于该 Controller 的全部方法，
没有单方法豁免。

```java
@LoginRequired
@GetMapping("/orders/mine")
List<Order> mine() { /* 业务 */ }
```

该注解只判断 `AuthContext` 是否存在，认证仍由 `AuthContextResolver` 和部署边界完成；能伪造 `X-Wuli3-Principal-*`
请求头的调用方同样会通过校验。直连暴露的服务必须替换默认解析器。只标注 `@Permission` 而没有 `@LoginRequired`
的接口，匿名请求由权限检查拒绝，错误码为 `PERMISSION.UNAUTHORIZED`，默认映射 400；需要 401 跳转登录时两个注解都要标注。

Boot 管理的 `RestClient.Builder` 和 `RestTemplateBuilder` 会自动安装出站拦截器。当前拦截器使用
`ContextPropagator.standardContextEncoder()`（包含 `InvocationContextCodec` 和 `AuthContextCodec`），
会传播 `X-Wuli3-Request-Id`、`X-Wuli3-Origin-Ip`、`X-Wuli3-Principal-Type`、`X-Wuli3-Principal-Id`，以及携带权限主体码时的
`X-Wuli3-Principal-Permission-Token`；
现有自动配置没有提供缩小该 HTTP 白名单的扩展点，因此出站目标必须是可信内部服务。

应用可以通过 Bean 替换以下默认 SPI：

- `RequestIdResolver`
- `ClientIpResolver`
- `AuthContextResolver`
- `WebErrorStatusResolver`
- `ErrorAlertNotifier`
- `ResourcePathResolver`

## JSON 配置

starter 会增量注册 wuli3 Java Time、`@ResourcePath` 和 `@Desensitized` Jackson 模块，不覆盖业务自己的
`Module` Bean。项目默认日期时间、Locale、时区和 Jackson feature 只在 `spring.jackson.*` 未显式配置时生效。

资源类型到域名的默认映射：

```properties
wuli3.web.json.resource.path.image=https://static.example.com
wuli3.web.json.resource.path.file=https://files.example.com
```

应用注册的 `DesensitizationStrategy` Bean 会覆盖同名内置策略；注册
`DesensitizationVisibilityPolicy` Bean 可以控制何时允许输出原值。详细 JSON API 见
[wuli3-json](wuli3-json.md)。

`ResourcePathResolver` 支持注册多个 Bean，starter 会按 Spring `@Order` 排序组合；同一资源类型由排序靠前的
解析器处理。没有匹配解析器时，资源路径保持原值。

## 统一响应启用方式

引入 `wuli3-web-spring-boot-starter` 后，默认自动启用：

- Controller 成功返回值统一包装。
- 业务异常和常见 Spring MVC 异常统一转换。
- 响应中自动携带当前请求上下文里的 `requestId`。

可通过配置关闭或调整：

```properties
wuli3.web.response.wrapper-enabled=true
wuli3.web.response.exception-handler-enabled=true
wuli3.web.response.wrap-response-entity-body=true
wuli3.web.response.success-message=
wuli3.web.response.validation-detail-enabled=true
```

应用侧扩展优先使用文档中列出的 SPI，例如 `WebErrorStatusResolver`、`ErrorAlertNotifier`、`RequestIdResolver` 和 `ClientIpResolver`。

## 错误告警扩展

业务系统可以实现 `ErrorAlertNotifier` 并注册为 Spring Bean，用于把统一异常处理捕获到的错误发送到日志、监控、IM、工单或告警平台。starter 只预留通知接口，不提供具体告警实现。

```java
@Component
final class BizErrorAlertNotifier implements ErrorAlertNotifier {

    @Override
    public void alert(final ErrorAlertContext context) {
        if (context.status().is5xxServerError()) {
            // 业务侧自行接入告警平台。
        }
    }
}
```

调用规则：

- 统一异常处理捕获异常并确定响应状态后调用告警通知。
- 默认只通知 5xx，或严重度为 `CRITICAL`、`FATAL` 的 `ErrorCodeException`；严重度只影响告警，不参与 HTTP 状态判定。
- 可同时注册多个 `ErrorAlertNotifier` Bean；调用顺序遵守 Spring `@Order` / `Ordered`。
- 没有注册 Bean 时不执行任何告警逻辑。
- 告警实现抛出 `RuntimeException` 时会被 starter 捕获并记录 warn 日志，不会影响原始错误响应。
- 通知器在请求线程同步依次执行；外部通知耗时会延长返错时间。需要异步投递时由应用提供有界队列及饱和、关闭策略。

`ErrorAlertContext` 字段：

| 字段 | 说明 |
| --- | --- |
| `error` | 原始异常对象。 |
| `status` | 即将返回的 HTTP 状态码。 |
| `responseCode` | 即将返回给调用方的错误码；可见性隐藏后会是对外错误码。 |
| `requestId` | 当前请求 ID，可能为 `null`。 |
| `method` | HTTP 方法。 |
| `requestUri` | 请求 URI。 |
| `remoteAddr` | 请求来源地址。 |

告警通知拿到的是从当前请求中提取出的不可变快照，不会把原始 `HttpServletRequest` 传给业务实现。

### 结构化错误日志

MVC 统一异常出口对最终 5xx，以及严重度为 `CRITICAL` / `FATAL` 的错误记录一条 ERROR 日志；即使没有配置告警通知器也会记录。普通 4xx 不输出这类错误日志。事件名固定为 `web.request.failed`，message 固定为 `Web request failed`，原始异常通过日志 cause 保留完整堆栈。

日志采用 SLF4J 原生键值字段，便于按字段检索，不依赖解析 `ErrorCodeCarrier.toString()`：

| 字段 | 含义 |
| --- | --- |
| `eventName` | 固定事件名 `web.request.failed`。 |
| `httpStatus` | 最终 HTTP 状态，按数值输出。 |
| `errorCode` | 原始诊断错误码。 |
| `responseCode` | 执行可见性规则后对外返回的错误码。 |
| `errorOrigin` / `errorSeverity` / `errorVisibility` | 最终责任归属、严重度和可见性。 |
| `sourceService` | 错误来源服务。 |
| `httpMethod` / `httpPath` | 请求方法和 URI 路径，不附带查询串。 |
| `httpRequestId` | 本次响应使用的请求 ID，缺失时为 null。与已有 MDC `requestId` 分开，避免 ECS 重复成员冲突。 |
| `exceptionType` | 最外层原始异常类型，与记录的堆栈一致。 |

默认文本格式通过 `%kvp` 显示这些字段。需要 JSON 日志时使用既有配置：

```properties
wuli3.logging.format=logstash
```

Logstash 输出示例片段如下，省略时间、Logger 和 MDC 等标准字段：

```json
{
  "message": "Web request failed",
  "eventName": "web.request.failed",
  "httpStatus": 500,
  "errorCode": "REMOTE.ORIGINAL.FAILURE",
  "responseCode": "SYSTEM.INTERNAL_ERROR",
  "errorOrigin": "SERVER",
  "errorSeverity": "CRITICAL",
  "errorVisibility": "INTERNAL",
  "sourceService": "inventory-service",
  "httpMethod": "POST",
  "httpPath": "/orders/42",
  "httpRequestId": "rid-logging",
  "exceptionType": "java.util.concurrent.CompletionException",
  "stack_trace": "原始异常及 cause 堆栈"
}
```

`ecs`、`gelf` 同样使用既有编码器；GELF 的附加字段按该格式加 `_` 前缀。MDC 中的 `requestId`、`trace_id`、`span_id` 保持原有传播规则，记录错误时不额外修改 MDC。

日志不主动附加查询串、请求头或正文；原始堆栈中的异常消息仍按既有规则记录，不自动脱敏。本节仅描述 MVC 异常出口，不扩大容器 `/error` 的日志覆盖范围。

## 标准响应结构

成功和失败的统一响应都使用 `ApiResponse<T>`：

```json
{
  "code": "0",
  "message": "",
  "timestamp": 1780000000000,
  "requestId": "request-id",
  "data": {}
}
```

字段含义：

| 字段 | 说明 |
| --- | --- |
| `code` | 外部错误码。成功固定为 `0`；失败由 `ErrorResolver` 解析。 |
| `message` | 成功时使用 `wuli3.web.response.success-message`；失败时使用错误消息或框架映射消息。 |
| `timestamp` | 响应生成时间，Unix 毫秒时间戳。 |
| `requestId` | 当前请求 ID，来自 `X-Wuli3-Request-Id` 或 starter 自动生成的 ID；上下文缺失时可能为 `null`。 |
| `data` | 成功数据；失败时一般为 `null`，参数校验失败时可为 `ValidationErrorDetails`。 |

## 成功返回规则

普通 Controller 返回值会被包装：

```java
@GetMapping("/users/{id}")
UserDetail getUser(@PathVariable final Long id) {
    return userService.get(id);
}
```

返回：

```json
{
  "code": "0",
  "message": "",
  "timestamp": 1780000000000,
  "requestId": "request-id",
  "data": {
    "id": 1
  }
}
```

成功返回矩阵：

| 场景 | HTTP 状态 | 响应 body |
| --- | --- | --- |
| 普通对象、集合、`null` | 保留原状态，通常 200 | 包装为 `ApiResponse.success(data)`。 |
| `String` | 保留原状态 | 序列化为 JSON 字符串形式的 `ApiResponse`，并设置 `Content-Type: application/json`。 |
| 已返回 `ApiResponse<?>` | 保留原状态 | 原样返回，不重复包装。 |
| 已返回 `ProblemDetail` | 保留原状态 | 原样返回。 |
| `ResponseEntity<T>` 且 `wrap-response-entity-body=true` | 保留 `ResponseEntity` 状态和响应头 | body 继续按普通返回值规则包装。 |
| `ResponseEntity<T>` 且 `wrap-response-entity-body=false` | 保留 `ResponseEntity` 状态和响应头 | body 原样返回。 |
| `byte[]`、`Resource`、`ResponseBodyEmitter`、`StreamingResponseBody` | 保留原状态 | 原样返回，不包装。 |
| HTTP 204 或 304 | 204 或 304 | 原样返回，不写 body。 |

## 原生响应

使用 `@NativeResponse` 可以跳过成功响应包装：

```java
import com.kjs.wuli3.web.response.NativeResponse;

@NativeResponse
@GetMapping("/download")
Resource download() {
    return resource;
}
```

注解可放在 Controller 类或方法上；方法级配置优先于类级配置。

| 配置 | 成功响应 | 异常响应 |
| --- | --- | --- |
| `@NativeResponse` 或 `@NativeResponse(SUCCESS_ONLY)` | 不包装，原样返回 | 仍返回统一错误 `ApiResponse`。 |
| `@NativeResponse(ALL)` | 不包装，原样返回 | 返回 Spring 标准 `ProblemDetail`，不使用 `ApiResponse` 外壳。 |

`NativeResponseMode.ALL` 下，业务异常生成的 `ProblemDetail` 会额外带上：

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "错误消息",
  "code": "SERVICE.MODULE.ERROR_NAME",
  "requestId": "request-id"
}
```

## 业务异常

业务代码推荐抛出 `ErrorCodeException`：

```java
import com.kjs.wuli3.core.error.ErrorCodeException;

throw new ErrorCodeException(UserErrors.USER_NOT_FOUND);
```

对于 `ErrorCodeException`，默认 HTTP 状态由 `ErrorOrigin` 决定。本地 `WebErrors` 协议错误保留与责任一致的具体 4xx 或 5xx；其他错误按责任回退为 400 或 500。`ErrorSeverity` 只决定告警优先级：

| 条件 | HTTP 状态 | `code` | `message` |
| --- | --- | --- | --- |
| `ErrorOrigin.CALLER` | 已知 Web 协议 4xx，否则 400 | 真实业务错误码，除非可见性隐藏 | 异常消息或错误码默认消息，除非可见性隐藏 |
| `ErrorOrigin.SERVER` | 已知 Web 协议 5xx，否则 500 | 真实业务错误码，除非可见性隐藏 | 异常消息或错误码默认消息，除非可见性隐藏 |
| `ErrorVisibility.PUBLIC`（默认） | 按 origin | 真实业务错误码 | 真实错误消息 |
| `ErrorVisibility.CODE_ONLY` | 按 origin | 真实业务错误码 | 按责任选择的安全占位消息 |
| `ErrorVisibility.MESSAGE_ONLY` | 按 origin | 按责任选择的占位码 | 真实错误消息 |
| `ErrorVisibility.INTERNAL` | 按 origin | 按责任选择的占位码 | 按责任选择的安全占位消息 |

### 错误元数据系统

wuli3-core 错误模型使用 `@ErrorMetadata` 注解声明错误的语义属性：

- **ErrorOrigin**（责任归属）：`CALLER`（调用方可修正）或 `SERVER`（服务端问题）
  - 约束 HTTP 状态系列：CALLER 使用 4xx，SERVER 使用 5xx；保留同系列的本地 Web 协议状态，否则分别回退为 400、500
  - 对于跨服务传播的错误（`ErrorCodeCarrier`），origin 会被保留，确保责任归属一致性

- **ErrorSeverity**（严重程度）：`NORMAL`、`WARNING`、`CRITICAL`、`FATAL`
  - 只影响告警：CRITICAL 和 FATAL 级别的错误会触发告警通知，即使是 4xx 状态
  - 不影响 HTTP 状态码判定

- **ErrorVisibility**（边界可见性）：控制错误信息在 HTTP 边界的暴露范围
  - `PUBLIC`：错误码和消息都对外输出（默认）
  - `CODE_ONLY`：只输出错误码，消息替换为按责任选择的安全消息
  - `MESSAGE_ONLY`：只输出消息，错误码替换为按责任选择的占位码
  - `INTERNAL`：错误码和消息都隐藏，使用按责任选择的占位值

可见性策略优先级：运行时覆盖（`withVisibility()`）> 字段级 `@ErrorMetadata` > 类级 `@ErrorMetadata` > 模块默认

core 的 `ErrorResolver` 统一执行错误码和消息的可见性策略，CALLER 隐藏码使用 `COMMON.REQUEST_REJECTED`、安全消息为“请求未被接受”；SERVER 使用 `SYSTEM.INTERNAL_ERROR`、安全消息为“内部错误”。责任与可见性独立，`CALLER + INTERNAL` 和 `SERVER + PUBLIC` 均合法。
Web 层复用其生成的 `ErrorCodeCarrier`，仅输出 `code/message`；`originalCode`、来源服务与诊断元数据不进入公开响应。
`WebErrorResponseMapper` 只承担 HTTP 响应映射，HTTP 状态、请求 ID 和验证明细仍由 Web 层处理。

普通业务错误可以使用默认的 `CALLER`。数据库、缓存、消息投递、JSON 序列化等只能由服务端修复的错误，应在错误码类型或常量的 `@ErrorMetadata` 中声明 `origin = ErrorOrigin.SERVER`。应用可以注册 `WebErrorStatusResolver` 覆盖默认映射。

`WebErrors.SERVICE_UNAVAILABLE` 默认返回 503，`WebErrors.TOO_MANY_REQUESTS` 默认返回 429。若通过 `withOrigin(...)` 把前者改为 CALLER，则回退为 400；把后者改为 SERVER，则回退为 500。状态映射不能违背运行时责任。传播错误的名称不参与猜测：远程 `WEB.SERVICE_UNAVAILABLE` 仍按传播的责任回退，需要具体状态时应在消费端显式映射。

错误码格式由 `ErrorResolver` 生成：

```text
SERVICE_CODE.ERROR_MODULE.ERROR_NAME
```

`SERVICE_CODE` 来自公共 Spring 基础模块的 `ApplicationServiceProperties`：优先取 `application.service.service-code`，未配置或为空白时取 `spring.application.name`，两者均为空时省略。服务标识最多 64 个字母、数字、点、下划线或连字符；输出错误码时转为大写。升级后，仅配置应用名的服务也会自动带服务前缀，客户端需同步调整错误码匹配。

Dubbo 等协议适配层接收到跨边界传播的错误时会抛出携带 `ErrorCodeCarrier` 的 `ErrorCodeException`。`ErrorCodeCarrier` 是 `ErrorCode` 的远程实现，携带原始错误码、**完整错误码和消息**、来源、责任、严重程度以及最终对外可见性，不需要在消费端伪造业务枚举。Web starter 会沿用现有 `ErrorCodeException` 链路：

- `ErrorOrigin.CALLER` 默认返回 400，`ErrorOrigin.SERVER` 默认返回 500（origin 在传播时保留）
- 新协议保留 visibility，Web 执行最终脱敏；消费方可通过 `withOrigin(...)` 与 `withVisibility(...)` 独立覆盖，单次覆盖优先于远端元数据。旧协议缺少 visibility 时按 PUBLIC 解释，先升级消费方再升级提供方
- `ErrorSeverity.CRITICAL` 和 `FATAL` 仍会触发错误告警（severity 在传播时保留）
- 消费方不需要依赖提供方的业务错误枚举，远程完整错误码也不会被替换为 Dubbo 自己的错误码

`ErrorCodeException` 是项目唯一错误异常。协议适配层只负责把传播载荷放入它，普通业务代码仍应声明枚举 `ErrorCode` 并抛出 `ErrorCodeException`。

## 框架异常

统一异常处理覆盖的 Spring MVC 常见异常：

| 异常类型 | HTTP 状态 | 统一错误码 | 说明 |
| --- | --- | --- | --- |
| `MethodArgumentNotValidException` | 400 | `WEB.BAD_REQUEST` | Bean Validation 参数校验失败。 |
| `ConstraintViolationException` | 输入 400 / 返回值 500 | 按状态映射 | 返回值约束属于服务端错误。 |
| `MissingServletRequestParameterException` | 400 | `WEB.BAD_REQUEST` | 缺少必填请求参数。 |
| `MethodArgumentTypeMismatchException` | 400 | `WEB.BAD_REQUEST` | 请求参数类型转换失败。 |
| `ServletRequestBindingException` | 框架指定状态 | 按状态映射 | 尊重 ErrorResponse 状态，例如服务端缺失路径变量为 500。 |
| `HttpMessageNotReadableException` | 400 | `WEB.BAD_REQUEST` | JSON 或请求体不可读。 |
| 裸 `IllegalArgumentException`（含 `NumberFormatException`） | 500 | `WEB.INTERNAL_ERROR` | 不能据此证明请求有错；明确的参数错误使用 Asserts 或显式映射。 |
| `IllegalStateException`、`NullPointerException` | 500 | `WEB.INTERNAL_ERROR` | 未识别内部故障。 |
| `HttpRequestMethodNotSupportedException` | 405 | `WEB.METHOD_NOT_ALLOWED` | HTTP 方法不支持。 |
| `HttpMediaTypeNotSupportedException` | 415 | `WEB.UNSUPPORTED_MEDIA_TYPE` | 请求媒体类型不支持。 |
| `NoHandlerFoundException`、`NoResourceFoundException` | 404 | `WEB.NOT_FOUND` | MVC handler 或静态资源不存在。 |
| `ResponseStatusException` | 异常指定状态 | 按状态映射 | 使用安全默认提示；需要公开自定义消息时使用 WebErrorMapper。 |
| `ErrorResponseException` | 异常指定状态 | 按状态映射 | 重建安全响应，不透传异常自带 body 或扩展属性。 |
| `HttpMessageNotWritableException` | 500 | `WEB.INTERNAL_ERROR` | 响应写出失败，不暴露内部细节。 |
| `ErrorCodeException`（传播错误） | 按传播错误的 `ErrorOrigin` | 按当前 Web 边界可见性策略 | 协议适配层接收的跨边界传播错误。 |
| 其他 `Exception` | 500 | `WEB.INTERNAL_ERROR` | 未分类异常。 |

状态与专用错误码由 `WebErrors` 统一声明，覆盖 28 个有效 4xx 和 10 个有效 5xx 协议状态。异常解析与响应投影共用此声明，MVC advice、原生 `ProblemDetail` 和容器错误出口保持一致。例如：

| HTTP 状态 | 错误码 |
| --- | --- |
| 401 | `WEB.UNAUTHORIZED` |
| 403 | `WEB.FORBIDDEN` |
| 404 | `WEB.NOT_FOUND` |
| 405 | `WEB.METHOD_NOT_ALLOWED` |
| 413 | `WEB.PAYLOAD_TOO_LARGE` |
| 415 | `WEB.UNSUPPORTED_MEDIA_TYPE` |
| 421 | `WEB.MISDIRECTED_REQUEST` |
| 422 | `WEB.UNPROCESSABLE_ENTITY` |
| 429 | `WEB.TOO_MANY_REQUESTS` |
| 500 | `WEB.INTERNAL_ERROR` |
| 502 / 503 / 504 | `WEB.BAD_GATEWAY` / `WEB.SERVICE_UNAVAILABLE` / `WEB.GATEWAY_TIMEOUT` |
| 未覆盖的 4xx | `WEB.BAD_REQUEST` |
| 未覆盖的 5xx | `WEB.INTERNAL_ERROR` |

未分配、未使用或已废止状态（如 418、419、420、509、510）不新增专用错误码。Spring 未定义的数值状态仍按既有规则归一化：非标准 4xx 回退为 400，其他非标准或非错误状态回退为 500。Spring 6.2 对 421 使用历史名称，starter 按数值保留 421，并在生成 `ProblemDetail` 时使用当前标题 `Misdirected Request`。

兼容性提示：405、415、429、503 等响应的 HTTP 状态保持原有语义，正文 `code` 从通用 `WEB.BAD_REQUEST` / `WEB.INTERNAL_ERROR` 细化为对应专用错误码。依赖旧通用错误码做分支判断的客户端需要同步适配。现有错误码名称和默认消息保持不变。

starter 不直接依赖 Spring Security。当前异常或受支持的透明包装内的异常属于以下类型时，映射为安全错误：

| 类名 | HTTP 状态 | 错误码 |
| --- | --- | --- |
| `org.springframework.security.core.AuthenticationException` | 401 | `WEB.UNAUTHORIZED` |
| `org.springframework.security.access.AccessDeniedException` | 403 | `WEB.FORBIDDEN` |

### 容器错误处理边界

starter 在 Spring Boot 的 `ErrorMvcAutoConfiguration` 之前注册应用级 `WebErrorController`，接管容器可转交的 `/error` 请求。不存在的路径、`HttpServletResponse.sendError(...)` 以及未处理异常触发的 `ERROR` dispatch 均使用应用级默认 `ApiResponse` 格式；此类请求没有 Controller 方法，不能依赖 `@NativeResponse`。响应保留容器确定的标准错误状态；非标准或非错误状态使用与 MVC 相同的归一化规则，错误码和消息通过同一 Web 错误投影生成。

容器提供的异常消息、堆栈、原始异常类型和扩展属性不直接输出。响应已提交后不尝试重写，只保留容器日志或应用诊断记录。非法 HTTP 报文、请求头限制等在进入应用前由容器或网关拒绝的请求不属于 starter 可接管范围，应由容器配置或网关错误页统一处理。

当前嵌入式容器集成测试覆盖项目默认的 Tomcat。Jetty、Undertow 只有在项目引入对应 starter 并启用相应测试运行配置后才执行同一组容器契约测试；不能将默认 Tomcat 测试结果宣称为其他容器已验证。

### 自定义 Web 异常识别

注册 `WebErrorMapper` Bean 可将异常转换为明确的 `ErrorCodeException`。多个 mapper 按 Spring 顺序调用，第一个非 null 结果生效；返回 null 继续默认分类。优先级为 Web 显式映射 > 单次异常覆盖 > 远端元数据或本地错误声明 > 框架默认。HTTP 状态仍可通过 `WebErrorStatusResolver` 自定义。

默认出口按以下顺序一次识别错误语义与协议信息：

1. 执行显式 mapper；命中后使用映射结果，不继承原异常的协议头或校验详情。
2. 识别当前异常的业务错误、Spring `ErrorResponse`、`@ResponseStatus`、安全异常或明确的请求输入错误。
3. 当前层未声明语义时，仅解包 `CompletionException`、`ExecutionException`、`InvocationTargetException`，然后重复识别；循环包装安全终止。
4. 普通异常及未知错误按 500 处理，不访问其内部 cause 来推断协议或业务语义。

业务错误保留原异常及其全部元数据，框架错误仅采用安全默认码和消息。HTTP 状态、错误码、协议头来源保存为一个内部结果；不会为提取头再次遍历异常链，也不会为框架错误合成新的 `ErrorCodeException` 堆栈。公开可见性统一由 core 应用一次，再选择 `ApiResponse` 或 `ProblemDetail` 格式。

裸 `IllegalArgumentException` 不再默认表示请求错误。公共请求参数校验应使用 `Asserts` 产生明确的 `ErrorCodeException`；第三方请求异常可用 `WebErrorMapper` 显式归类。

自定义 `WebErrorStatusResolver` 在默认识别后调用一次，接收原始或显式映射后的异常以及实际已识别错误码。对业务错误仅覆盖 HTTP 状态，不改写业务码或元数据；对框架错误则按最终状态选择协议码。状态变化时丢弃原协议头，返回 2xx、3xx 等非错误状态时归一化为 500。直接调用默认状态 SPI 时仍兼容候选错误码回退，默认内部链路不使用 `INTERNAL_ERROR` 表达“未分类”。

所有错误经同一投影路径输出；只有首次识别为请求校验错误、PUBLIC 且最终为 4xx 时才允许返回结构化详情。状态 SPI 将服务端错误改成 4xx 不会赋予其公开详情的资格。INTERNAL、CODE_ONLY、MESSAGE_ONLY 均不附带字段详情，避免内容绕过策略。原生 ProblemDetail 重新生成，只保留安全码、消息和 requestId；响应头仅保留 Allow、Accept、Retry-After、WWW-Authenticate 等明确协议头。MVC advice 不覆盖所有 Servlet Filter 或已提交响应。

本次边界整理的兼容性变化：裸 `IllegalArgumentException` 从 400 改为 500；普通 `RuntimeException(cause)` 不再自动继承内部状态和业务语义，需要保留时应显式映射。自定义状态 SPI 收到实际协议码，而非未分类占位码；显式 mapper 不再隐式携带原异常的头或校验详情。透明包装内的业务错误会完整保留责任及可见性，避免先前只保留状态而丢失元数据。

既有可见性契约保持：隐藏调用方错误使用 `COMMON.REQUEST_REJECTED`，服务端使用 `SYSTEM.INTERNAL_ERROR`；`ResponseStatusException` / `ProblemDetail` 不直接暴露原始消息和扩展属性。

## 参数校验详情

`wuli3.web.response.validation-detail-enabled=true` 时，以下异常会在失败响应的 `data.errors` 中返回字段级详情：

- `MethodArgumentNotValidException`
- `ConstraintViolationException`（非空请求约束集合，不包含返回值约束）
- `MissingServletRequestParameterException`
- `MethodArgumentTypeMismatchException`

示例：

```json
{
  "code": "WEB.BAD_REQUEST",
  "message": "请求参数错误",
  "timestamp": 1780000000000,
  "requestId": "request-id",
  "data": {
    "errors": [
      {
        "field": "name",
        "code": "NotBlank",
        "message": "must not be blank"
      }
    ]
  }
}
```

关闭字段级详情：

```properties
wuli3.web.response.validation-detail-enabled=false
```

关闭后，失败响应只返回统一 `message`，`data` 为 `null`。

字段详情只包含稳定的 `field`、`code` 和 `message`，不会回显 rejected value。
`ErrorCodeException` 不支持附加任意 `detail` 对象；业务需要返回其他结构化明细时，应在应用自己的异常处理器中显式定义响应 DTO 和可见性规则。

## 未覆盖场景

以下场景不由 `response` 包保证统一处理：

- Spring MVC 之外的请求链路，例如 Servlet Filter 在进入 `DispatcherServlet` 前直接写出的响应。
- 容器级错误页、容器级 404、Tomcat/Jetty/Undertow 自己生成的错误响应。
- WebFlux 响应链路；本 starter 面向 Spring MVC。
- 异步任务、消息消费、定时任务等非 HTTP 调用链路中的异常。
- 已经手动写入 `HttpServletResponse` 的接口，例如直接调用 `getWriter()` 或 `getOutputStream()` 输出。
- 文件下载、字节数组、`Resource`、`ResponseBodyEmitter`、`StreamingResponseBody` 等原生或流式响应 body。
- HTTP 204 和 304 响应 body。
- 自定义异常类型的细粒度业务语义；未转换为 `ErrorCodeException`、`ResponseStatusException` 或 `ErrorResponseException` 的异常会按兜底 `Exception` 处理。
- `@NativeResponse(ALL)` 下 `ErrorResponseException` 会返回异常自带 body，starter 不重写其中字段。

如果业务需要覆盖这些场景，应在应用侧增加更靠近场景的处理，例如自定义 Filter、容器错误页、专用 `@ControllerAdvice`、或显式抛出 `ErrorCodeException`。

## 验证

```bash
./gradlew :wuli3-web-spring-boot-starter:test
./gradlew :wuli3-web-spring-boot-starter:check
```

### 可信权限主体码

`AuthContext` 另携带 `X-Wuli3-Principal-Permission-Token`，表示可信入口确定的可轮换授权版本。
缺码时仍可恢复认证身份，但权限模块拒绝鉴权；非空码必须非空白。
所有标准传播字段现使用 `X-Wuli3-*`，不读取旧字段。Higress 外部入口须先按前缀（忽略大小写）删除全部伪造头，再写入认证结果。
具体清洗插件及配置在网关工程部署，参见 [权限模块的入口契约](wuli3-permission.md#higress-可信入口契约)。
