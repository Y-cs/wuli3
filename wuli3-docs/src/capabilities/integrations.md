---
description: Dubbo 调用上下文与错误传播，以及 Aliyun OSS 多 profile 客户端的接入边界。
---

# 外部系统集成

外部系统 starter 只把稳定契约接到具体 SDK，不替应用决定注册中心、认证策略、资源权限或业务重试。接入时先明确数据和错误边界，再选择适配器。

## Dubbo：可信内部 RPC

Dubbo starter 使用原生 Filter 在 consumer 与 provider 之间传播 Wuli3 上下文和错误 carrier。它不引入 Spring Cloud，也不决定注册中心；Nacos 等组件由应用按部署需要引入。

```yaml
wuli3:
  dubbo:
    context:
      enabled: true
    error:
      enabled: true
```

两个开关默认开启。应用可以提供 `ContextPropagator` Bean 调整传播字段，Filter 本身不应被业务重复实现。

### 上下文作用域

provider 进入服务方法前恢复附件中的上下文，并在方法结束后恢复 provider 线程原状态；requestId 在该窗口内进入日志 MDC。业务自己启动的异步任务不会自动继承，必须显式使用 `ContextProxy` 或已接入传播的执行器。

返回 `CompletableFuture` 只覆盖服务方法调用期间的作用域。Future 完成阶段需要重新建立传播作用域，不能假设仍然持有 provider 上下文。

### 错误传播

可预期业务失败使用 `ErrorCodeException`。Filter 只传播 core 的稳定字段：原始错误码、当前错误码、消息、责任、严重度、可见性和来源服务；不传业务枚举、Java 堆栈或原始异常对象。

Dubbo 是可信内部通道，会保留完整错误信息。HTTP 或其他公开出口才执行可见性投影，不能把 response attachment 直接复制到外部响应。

普通运行时异常继续遵循 Dubbo 原生规则。基础设施适配器应把可预期 SDK 失败转换为明确模块错误，启动配置错误则保留原生诊断。

多跳调用要按消费端先升级、提供端后升级的顺序处理可见性字段。旧消费端不理解新字段，可能错误公开完整消息；缺少必需字段或枚举非法时，应保留 Dubbo 原始异常。

### 注册中心与安全

注册中心地址、命名空间、认证和连接生命周期都由 Dubbo 应用配置。RPC 传播字段只适用于可信服务网络；网关与公网入口必须在 HTTP 边界重新验证身份，不能把 Dubbo 附件当作外部认证凭据。

## Aliyun OSS：多 profile 客户端

Aliyun starter 管理 OSS SDK V2 的命名客户端和生命周期，不封装上传、下载、删除等业务流程。每个 profile 隔离访问凭据与 OSS 连接参数。

```yaml
wuli3:
  aliyun:
    default-profile: default
    profiles:
      default:
        access-key:
          id: ${OSS_ACCESS_KEY_ID}
          secret: ${OSS_ACCESS_KEY_SECRET}
        oss:
          region: cn-hangzhou
          bucket: example-default
      archive:
        access-key:
          id: ${OSS_ARCHIVE_ACCESS_KEY_ID}
          secret: ${OSS_ARCHIVE_ACCESS_KEY_SECRET}
        oss:
          region: cn-shanghai
          bucket: example-archive
          endpoint: https://oss-cn-shanghai-internal.aliyuncs.com
```

当前配置转换为 SDK 静态凭据 Provider。仓库不应保存 AccessKey；生产环境使用最小权限凭据，并通过环境变量、容器 Secret 或外部密钥系统注入。若需要 STS、实例角色或自动轮换，应由应用替换凭据实现或在接入层封装。

每个 profile 必须提供 region 和 bucket；指定的 default-profile 必须存在。未配置默认 profile 时，调用方必须显式选择名称；请求不存在的 profile 会得到统一错误。

`OssClientManager` 负责创建、查找和关闭全部客户端。`OssTemplate` 对 SDK 操作统一转换异常，应用仍负责对象 key、Content-Type、签名 URL、下载权限和生命周期策略。

公网 Region 可以使用 SDK 默认 endpoint；内网、加速或自定义域名场景显式配置 endpoint。endpoint 不改变业务访问授权，签名和 Bucket 权限仍需由应用校验。

## 验收重点

Dubbo 需要验证上下文恢复、异步传播、业务错误多跳传播和旧版本升级；OSS 需要验证 profile 校验、凭据注入、客户端关闭、操作异常映射和访问权限。真实注册中心与对象存储集成不能由 starter 单元测试替代。

继续阅读[运行与运维](/capabilities/operations)，为这些外部客户端配置关闭阶段和可观察性。

## 源码与详细指南

- [Dubbo starter 指南](https://github.com/weiran-tech/wuli3/blob/main/guide/wuli3-dubbo-spring-boot-starter.md)
- [OSS profile 配置模型](https://github.com/weiran-tech/wuli3/blob/main/wuli3-aliyun-spring-boot-starter/src/main/java/com/kjs/wuli3/aliyun/autoconfigure/AliYunProperties.java)
