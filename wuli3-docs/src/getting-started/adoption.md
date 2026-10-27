---
description: 在已有 Gradle 或 Maven 项目中逐步接入 Wuli3，而不强制迁移工程结构。
---

# 接入已有项目

已有 Spring Boot 项目可以先复用稳定契约，再逐步采用 starter。不要为了使用一个断言或分页类型，先把整个应用重写成七模块。

## 先导入 BOM

Gradle：

```kotlin
repositories {
    mavenCentral()
    // 内部制品仓库按团队约定加入
}

dependencies {
    implementation(platform("com.kjs.wuli3:wuli3-dependencies:1.0.0"))
    implementation("com.kjs.wuli3:wuli3-core")
}
```

Maven：

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.kjs.wuli3</groupId>
      <artifactId>wuli3-dependencies</artifactId>
      <version>1.0.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

BOM 管版本，具体依赖仍由应用声明。只发布 BOM 不足以运行 starter；外部项目必须能解析对应模块和它们的传递依赖。

## 按边界选择 starter

| 先解决的问题 | 先接入 | 之后确认 |
| --- | --- | --- |
| 错误、断言、ID、时间 | `wuli3-core` | 错误模块命名和对外可见性 |
| HTTP 上下文、统一响应 | `wuli3-web-spring-boot-starter` | 认证来源和原有响应兼容 |
| 数据库分页与 MyBatis-Plus | `wuli3-mysql-spring-boot-starter` | 表结构、事务和排序白名单 |
| Redis 缓存或锁 | `wuli3-redis-spring-boot-starter` | TTL、失效、降级和命名空间 |
| 本地/事务/远程事件 | `wuli3-event-spring-boot-starter` | transport、幂等、重试与投递语义 |

连接地址、凭据、表结构、消息拓扑和认证服务都属于应用或部署环境；starter 不会凭空创建这些外部事实。

## 渐进采用的顺序

1. 先只接入 `core`，让边界输入都进入统一错误链。
2. 再接入 Web，确认认证解析器和已有响应契约。
3. 为一个完整用例接入真实持久化，先验证归属检查、事务和分页。
4. 需要跨执行边界时再加入上下文传播和事件 transport。
5. 最后接入审计、指标、RPC 或对象存储，并为每个外部端口补联调验收。

按这个顺序，每一步都能通过单元测试和最小运行测试独立回滚。继续阅读 [DDD 边界](/concepts/ddd)，理解代码应该放在哪个模块。
