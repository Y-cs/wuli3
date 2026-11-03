# wuli3-openfeign-spring-boot-starter 使用指南

## 1. 模块定位

该 starter 基于 Spring Cloud OpenFeign 的 `RequestInterceptor` 和 `ErrorDecoder` 提供两项能力：

- 在 Feign 出站请求中传播 Wuli3 调用上下文。
- 把 HTTP 响应头中 core 定义的 `ErrorCodeCarrier` 还原为 `ErrorCodeException`，避免调用方解析提供方的响应体结构。

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
`RequestInterceptor`。`ErrorDecoder` 只允许一个，应用注册自己的 Bean 时默认实现自动退让。

## 4. 上下文传播

`FeignContextRequestInterceptor` 在每次 Feign 调用前读取当前 `ContextSnapshot`，写入标准上下文字段，并先删除调用方已有的保留字段，避免业务代码伪造 `X-Wuli3-*` 值。

默认传播 `X-Wuli3-Request-Id`、`X-Wuli3-Origin-Ip`、`X-Wuli3-Principal-Type`、`X-Wuli3-Principal-Id` 和 `X-Wuli3-Principal-Permission-Token`。需要缩小字段时，替换应用侧的 `ContextPropagator` Bean。

提供方使用 `wuli3-web-spring-boot-starter` 时，`ContextFilter` 会从请求头恢复 `requestId` 和认证上下文，不需要额外配置。目标端必须属于可信内部服务。

## 5. 错误传播

### 5.1 边界模型

`FeignErrorDecoder` 只读取 core 的错误传播头：

```text
X-Wuli3-Error-Original-Code
X-Wuli3-Error-Code
X-Wuli3-Error-Message
X-Wuli3-Error-Origin
X-Wuli3-Error-Severity
X-Wuli3-Error-Visibility
X-Wuli3-Error-Source-Service
```

头字段完整时返回携带 `ErrorCodeCarrier` 的 `ErrorCodeException`，原始 `FeignException` 作为 cause 保留诊断信息。头字段缺失或枚举值非法时使用 Feign 默认解码器，保持原始异常。

HTTP 头名称按不区分大小写读取。错误协议只用于可信内部服务间传播，不能直接复制到公开响应。

### 5.2 提供方输出

提供方需要显式开启错误头输出：

```properties
wuli3.web.response.error-header-propagation-enabled=true
```

开启后 `WebExceptionHandler` 在错误响应头写入完整传播字段，响应体仍按 `visibility` 执行脱敏。该配置默认关闭，只能用于可信内部 HTTP 边界；对公网暴露的服务不要开启。

### 5.3 降级行为

| 场景 | 行为 |
| --- | --- |
| 提供方已开启错误头传播 | 消费方得到 `ErrorCodeException`，可使用 `asRemoteError()` 读取原始来源和可见性。 |
| 提供方未开启或使用其他框架 | 消费方得到 Feign 默认的 `FeignException`。 |
| 错误头字段缺失或非法 | 不推断业务错误，使用 Feign 默认解码。 |

## 6. 限制

- 上下文只覆盖 Feign 调用线程；业务自行创建的异步任务需要显式传播。
- starter 只处理经过 Feign 客户端的调用，不处理定时任务、消息消费或应用自行建立的线程边界。
- 错误契约不携带任意结构化 detail、Java 堆栈或异常类名。需要新的稳定字段时，应先扩展 core 的协议无关契约。

## 7. 验证

```bash
./gradlew :wuli3-openfeign-spring-boot-starter:test
./gradlew :wuli3-openfeign-spring-boot-starter:check
```
