# wuli3-openfeign-spring-boot-starter 使用指南

## 1. 模块定位

该 starter 基于 Spring Cloud OpenFeign 的 `RequestInterceptor` 和 `ErrorDecoder` 提供两项能力：

- 在 Feign 出站请求中传播 Wuli3 调用上下文。
- 把提供方 Web 统一错误响应体中的展示错误码还原为 `ErrorCodeException`，让调用方按错误码处理，不必解析响应体结构。

错误还原只读取响应体的 `code` 和 `message`，与提供方对外展示的内容一致，不要求提供方开启额外开关、也不要求修改响应头。

starter 不引入 `@EnableFeignClients`，也不决定服务发现和负载均衡；这些仍由应用的 Spring Cloud 配置负责。只有检测到 Feign 类时自动配置才生效。

## 2. 引入方式

```kotlin
dependencies {
    implementation("com.kjs.wuli3:wuli3-openfeign-spring-boot-starter")
}
```

starter 已经传递依赖 `wuli3-core` 和 `wuli3-context-propagation`。拦截器和错误解码器通过自动配置注册为 Spring Bean，Feign 客户端会自动收集。

## 3. 配置

```yaml
wuli3:
  openfeign:
    context:
      enabled: true
    error:
      enabled: true
```

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `wuli3.openfeign.context.enabled` | `true` | 是否注册出站上下文拦截器。 |
| `wuli3.openfeign.error.enabled` | `true` | 是否注册远程错误解码器。 |

上下文拦截器始终注册，只由 `wuli3.openfeign.context.enabled` 控制；Feign 会同时使用应用自己的其他
`RequestInterceptor`。`ErrorDecoder` 只允许一个，应用注册自己的 Bean 时默认实现自动退让；容器中没有
`ObjectMapper` 时也不注册，直接使用 Feign 默认解码。

## 4. 上下文传播

`FeignContextRequestInterceptor` 在每次 Feign 调用前读取当前 `ContextSnapshot`，先删除调用方已有的保留字段，再写入标准上下文字段，避免业务代码伪造 `X-Wuli3-*` 值。需要缩小字段时，替换应用侧的 `ContextPropagator` Bean。

提供方使用 `wuli3-web-spring-boot-starter` 时，`ContextFilter` 会从请求头恢复 `requestId` 和认证上下文，不需要额外配置。目标端必须属于可信内部服务。

## 5. 错误还原

### 5.1 读取规则

`FeignErrorDecoder` 在非 2xx 响应上读取 `Content-Type` 为 JSON 的响应体：

```json
{
  "code": "ORDER.NOT_FOUND",
  "message": "订单不存在",
  "timestamp": 1762300000000,
  "requestId": "r-1",
  "data": null
}
```

读取 `code` 和 `message` 后构造携带 `ErrorCodeCarrier` 的 `ErrorCodeException`，原始 `FeignException` 作为 cause 保留状态、地址和响应体。

响应体不携带责任归属、严重程度和来源服务，缺失部分按 HTTP 状态推导：

| 字段 | 来源 |
| --- | --- |
| `originalCode` / `code` | 响应体 `code`，两者相同（展示码已经过提供方可见性投影，原始码无法还原） |
| `message` | 响应体 `message` |
| `origin` | 4xx 为 `CALLER`，5xx 为 `SERVER`；这是 `ErrorOrigin` 声明的适配层映射规则 |
| `severity` | 固定 `NORMAL`，不根据状态推断告警级别 |
| `sourceService` | 空字符串；响应体不携带来源服务 |
| `visibility` | `PUBLIC`；响应体就是公开投影 |

### 5.2 降级行为

| 场景 | 行为 |
| --- | --- |
| 响应体含 `code` 和 `message` | 返回 `ErrorCodeException`，本地 Web 出口按 `origin` 映射 4xx/5xx。 |
| 提供方未开启统一响应、使用其他框架或返回 HTML 错误页 | `Content-Type` 不是 JSON，返回 Feign 按状态生成的 `FeignException`。 |
| 响应体缺少 `code`、不是合法 JSON、使用成功码 `0` | 不推断业务错误，返回 Feign 默认异常。 |
| 响应体为空 | 返回 Feign 默认异常。 |
| 网关或负载均衡产生的 502/503/504 | 通常没有统一响应体，返回 Feign 默认异常。 |

`FeignErrorDecoder` 不在客户端记录失败日志：还原出的 `ErrorCodeException` 会被抛出并沿调用链传播，由调用方的 Web 错误出口按 `origin` 和 `severity` 统一决定是否记录、是否告警。客户端再记一次会重复记录预期内的业务错误。

### 5.3 与 Dubbo 传播的差异

`wuli3-dubbo-spring-boot-starter` 在 RPC 附件中完整传播 `originalCode`、`origin`、`severity`、`sourceService` 和 `visibility`，支持多跳保留最初来源。HTTP 侧只还原提供方对外展示的 `code` 和 `message`，因此：

- 被提供方 `ErrorVisibility` 隐藏的原始错误码无法在调用方还原，`INTERNAL` / `CODE_ONLY` 的错误会以提供方投影后的占位码抵达；
- 多跳调用不保留最初来源服务，`sourceService` 为空；
- 需要完整诊断语义的链路应继续使用 Dubbo，或由提供方开启错误头传播后另行扩展解码器。

## 6. 限制

- 上下文只覆盖 Feign 调用线程；业务自行创建的异步任务需要显式传播。
- starter 只处理经过 Feign 客户端的调用，不处理定时任务、消息消费或应用自行建立的线程边界。
- 错误还原依赖 `jackson-databind`（`compileOnly`）；运行时不提供时自动解码器不注册。
- 错误契约不携带任意结构化 detail、Java 堆栈或异常类名。

## 7. 验证

```bash
./gradlew :wuli3-openfeign-spring-boot-starter:test
./gradlew :wuli3-openfeign-spring-boot-starter:check
```
