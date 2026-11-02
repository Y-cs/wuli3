# 从零创建一个 Wuli3 项目

本文以 `demo-order` 为例，完成工程生成、构建、启动和 HTTP 增删改查。示例基于当前仓库的 `1.0.0`、JDK 21、Gradle Wrapper 9.6.1 和 Spring Boot 3.5.15。命令使用 macOS/Linux shell；Windows 使用 `gradlew.bat`。

## 1. 先准备可解析的制品

安装 JDK 21 和 Git，确认 `java -version` 指向预期 JDK。生成工程自带 Gradle Wrapper，不需要另外安装 Gradle；首次运行仍需要下载 Gradle 和第三方依赖。

Wuli3 的生成器、BOM、starter 与构建约定插件都需要从 Maven 仓库解析。本文不假设这些快照已经发布到 Maven Central。按自己的环境选择下面一种准备方式。

### 方式 A：从当前源码发布到本机

在 **Wuli3 仓库根目录**执行：

```bash
./gradlew publishToMavenLocal :wuli3-build-logic:publishToMavenLocal
```

这会发布业务库、BOM、生成器和构建插件。只发布 BOM 不足以启动生成的工程；BOM 只管理版本，不包含 starter 或插件实现。发布到本机仅供开发验证，不会上传公司仓库。

生成工程默认包含 `mavenLocal()`，可以直接使用这些本机制品。源码有修改时，先重新发布再验证外部工程，避免用到旧快照。

### 方式 B：使用团队已发布的制品

确认仓库同时包含以下制品及其依赖：

- `com.kjs.wuli3:wuli3-ddd-generator:1.0.0`。
- `com.kjs.wuli3:wuli3-dependencies:1.0.0` 和要使用的 Wuli3 库。
- 对应版本的 `wuli3-build-logic` 实现及 Gradle 插件 marker。

JBang 的 `--repos` 只影响生成器自身解析，**不会自动修改生成工程的仓库配置**。工程生成后，在其 `settings.gradle.kts` 中同时为 `pluginManagement.repositories` 和 `dependencyResolutionManagement.repositories` 加入公司仓库。下面是需要合并到已有文件的片段，URL 必须替换为实际地址：

```kotlin
pluginManagement {
    repositories {
        maven("https://maven.example.com/repository/snapshots")
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.example.com/repository/snapshots")
        mavenCentral()
    }
}
```

有鉴权时通过团队约定的本机 Gradle 属性或环境变量提供凭据，不把密码写进工程。仓库需要允许解析 SNAPSHOT；版本选择也应以实际已发布版本为准。

## 2. 生成不依赖中间件的工程

以下两条路线任选其一；不要连续生成到同一个非空目录。

### 路线 A：JBang 运行已发布的生成器

已安装 JBang、并完成本地发布时，在放置新工程的父目录执行：

```bash
jbang --repos mavenlocal \
  com.kjs.wuli3:wuli3-ddd-generator:1.0.0 generate \
  --service demo-order --package com.example.order --domain order \
  --persistence none --messaging none --rpc none --registry none \
  --wuli3-version 1.0.0 --build-logic-version 1.0.0 \
  --output .
```

使用团队制品库时，把 `--repos mavenlocal` 改为 `--repos https://实际仓库地址`，并按上一节配置生成工程自己的仓库。

### 路线 B：直接从 Wuli3 源码运行生成器

无需 JBang。在 **Wuli3 仓库根目录**执行下面命令，示例将新工程放到仓库的同级目录：

```bash
./gradlew :wuli3-ddd-generator:run \
  --args="generate --service demo-order --package com.example.order --domain order --persistence none --messaging none --rpc none --registry none --wuli3-version 1.0.0 --build-logic-version 1.0.0 --output $PWD/.."
```

直接运行生成器只解决“生成文件”，生成工程构建时仍需要第一节准备的 BOM、插件和库。

`--output` 表示**父目录**，最终工程路径为 `<output>/demo-order`。目标目录必须不存在或为空，生成器不会覆盖业务代码。生成器也会尝试初始化 Git 并配置 `.githooks`。

参数选择规则：

| 参数 | 首次体验 | 接入真实设施时 |
| --- | --- | --- |
| `--persistence` | `none`，进程内存储 | `mysql`，生成 PO、Mapper 和扫描配置 |
| `--messaging` | `none` | `rocketmq` 或 `rabbitmq`，加入对应 starter |
| `--rpc` | `none` | `dubbo`，在 bootstrap 发布 Facade |
| `--registry` | `none` | `nacos`，必须同时启用 Dubbo |

消息选项不会生成具体的消费协议或 topic/queue 配置；MySQL 选项也不替你准备数据库与业务表。先跑通无中间件版本，再参考各 starter 指南补齐连接、表结构和业务配置。

## 3. 构建、启动和访问

进入刚生成的 `demo-order` 目录，然后运行：

```bash
./gradlew check
./gradlew spotlessCheck
./gradlew :bootstrap:bootRun
```

`check` 包含编译、测试、Checkstyle、Forbidden APIs、Error Prone、NullAway 和 DDD 架构检查。`spotlessCheck` 单独检查格式；需要修复格式时运行 `./gradlew spotlessApply`。

默认业务端口为 `8080`，管理端口为 `8099`。保持启动终端运行，在另一个终端依次执行以下请求。命令直接调用生成器已经提供的接口，不需要先添加 Controller。

### 新增记录

```bash
curl -i -X POST http://localhost:8080/api/order \
  -H 'Content-Type: application/json' \
  -d '{"id":{"value":"order-1"},"status":"ACTIVE"}'
```

成功响应的业务部分如下（省略动态的 `timestamp` 和 `requestId`）：

```json
{"code":"0","message":"","data":{"id":{"value":"order-1"},"status":"ACTIVE"}}
```

`id` 是 `EntityId` 值对象，所以请求使用 `{"value":"order-1"}`，不是直接传字符串。标识由调用方提供，示例不会自动生成。

### 查询、修改与分页

```bash
curl -i http://localhost:8080/api/order/order-1

curl -i -X POST http://localhost:8080/api/order \
  -H 'Content-Type: application/json' \
  -d '{"id":{"value":"order-1"},"status":"INACTIVE"}'

curl -i -X POST http://localhost:8080/api/order/page \
  -H 'Content-Type: application/json' \
  -d '{"pageNumber":1,"pageSize":20,"sorts":[{"field":"id","direction":"ASC"}]}'
```

保存同一标识会更新其状态。分页响应的 `data` 包含 `items`、`total`、`pageNumber` 和 `pageSize`；全新工程只执行上述命令时，`total` 应为 `1`，记录状态为 `INACTIVE`。页码从 `1` 开始，每页最大 `200` 条；内存示例允许按 `id` 和 `status` 排序。

### 删除并确认结果

```bash
curl -i -X DELETE http://localhost:8080/api/order/order-1

curl -i -X POST http://localhost:8080/api/order/page \
  -H 'Content-Type: application/json' \
  -d '{"pageNumber":1,"pageSize":20,"sorts":[]}'
```

此时 `data.items` 应为空、`data.total` 应为 `0`。删除不存在的记录也是幂等操作。再按 ID 查询已删除的记录会进入统一错误码响应，不会返回成功的空对象；不要把业务“记录不存在”直接等同于 HTTP 路由的 `404`。

还可以验证边界校验：

```bash
curl -i -X POST http://localhost:8080/api/order/page \
  -H 'Content-Type: application/json' \
  -d '{"pageNumber":0,"pageSize":20,"sorts":[]}'
```

该请求应失败，响应 `code` 不为 `"0"`。这些读写数据只存在于当前进程，停止并重启应用后清空；内存示例也不提供数据库事务回滚语义。

### 打包运行

在启动终端按 Ctrl+C 停止服务后：

```bash
./gradlew :bootstrap:bootJar
java -jar bootstrap/build/libs/bootstrap.jar
```

生成的配置在 `bootstrap/src/main/resources/application.yml`。其中管理端口默认暴露 `/__core/health` 和 `/__core/shutdown`；部署前应按 [安全边界指南](../security-boundaries.md) 限制管理端口访问，并按需求关闭 shutdown。

## 4. 新业务代码应该放在哪里

下面的包路径都以 `com.example.order` 为前缀；Java 文件位于对应模块的 `src/main/java` 下。

| 模块 | 示例位置 | 你要写的内容 |
| --- | --- | --- |
| `shared-kernel` | `sharedkernel.EntityId`、`Status`、`RecordErrors` | 共享标识、稳定状态和错误约定，保持最小化 |
| `domain` | `domain.order.Order`、`OrderRepository` | 聚合、不变量、领域行为和聚合仓储接口，不引入 Spring |
| `api` | `api.dto.order.OrderStatusDto`、`api.facade.OrderFacade` | 内部调用契约与传播对象 |
| `app` | `app.service.order.OrderService`、`app.repository.order.OrderQueryRepository` | 完整用例、事务边界和本地查询接口 |
| `infra` | `infra.persistence.order.OrderRepositoryImpl`、`OrderQueryRepositoryImpl` | 仓储实现、持久化转换；外部系统适配放 `infra.gateway` |
| `adapter` | `adapter.api.order.OrderApi` | HTTP、MQ、定时任务入口；解析协议后委托应用服务 |
| `bootstrap` | `OrderServiceApplication`、`application.yml` | 启动与最终装配、部署配置、RPC 发布 |

例如新增“取消订单”用例：在 `domain.order.Order` 定义状态转换及规则，在 `app.service.order` 加载聚合、执行业务行为并保存，在 `adapter.api.order` 暴露 HTTP 入口。如果内部 RPC 也需要此用例，再扩展 `api.facade` 和 `app.facade`，复用同一应用服务。

新增字段时同步考虑聚合、DTO、持久化对象及转换，不把数据库 PO 暴露给调用方。不必机械地为每层复制一套相同字段对象。架构边界由构建插件检查，详见 [DDD 约定](../ddd-conventions.md)。

## 5. 已有项目如何渐进接入

已有 Spring Boot 项目不必重新生成七模块。可以先导入 BOM，再按需添加工具库或 starter。以下都是**合并到已有文件的配置片段**，不是完整工程。

### Gradle 项目

在 `settings.gradle.kts` 的 `dependencyResolutionManagement.repositories` 配置本机或团队仓库；然后在业务模块的 `build.gradle.kts` 添加：

```kotlin
dependencies {
    implementation(platform("com.kjs.wuli3:wuli3-dependencies:1.0.0"))
    implementation("com.kjs.wuli3:wuli3-core")
    implementation("com.kjs.wuli3:wuli3-web-spring-boot-starter")
}
```

BOM 约束这些库的版本，允许省略各自的版本号；使用生成工程的 Wuli3 约定插件时已经自动导入 BOM，不要重复配置。

### Maven 项目

在 `pom.xml` 中合并：

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

<dependencies>
  <dependency>
    <groupId>com.kjs.wuli3</groupId>
    <artifactId>wuli3-core</artifactId>
  </dependency>
  <dependency>
    <groupId>com.kjs.wuli3</groupId>
    <artifactId>wuli3-web-spring-boot-starter</artifactId>
  </dependency>
</dependencies>
```

本地发布可直接从 Maven 本地仓库读取；远程仓库在 Maven `settings.xml` 或项目仓库配置中声明，并为快照启用 snapshots。BOM 不会替你配置 Maven 编译插件、Java 21 或 Spring Boot 打包插件；保留现有工程的构建配置，并核对其 Boot 版本与当前基线兼容。

Web starter 会带来统一响应、异常映射和请求上下文等行为。已有系统应先确认原有 HTTP 契约，再按 [Web 指南](../wuli3-web-spring-boot-starter.md) 配置相关开关；只想复用断言、错误模型或时间工具时，单独引入 `wuli3-core` 即可。

## 6. 常见卡点与后续阅读

| 现象 | 检查方向 |
| --- | --- |
| JBang 找不到生成器 | 制品是否已发布；JBang 是否配置正确仓库 |
| Gradle 找不到约定插件 | 是否发布 wuli3-build-logic 和 plugin marker；是否配置 `pluginManagement.repositories` |
| 找不到 BOM/starter | 不要只发布 BOM；检查工程依赖仓库和版本 |
| 生成器拒绝输出目录 | 输出父目录下的同名服务目录必须不存在或为空 |
| 启动端口冲突 | 同时检查业务 `8080` 和管理 `8099` 端口 |
| JSON 绑定失败 | `id` 是对象，状态枚举使用 `ACTIVE`/`INACTIVE`，分页字段是 `pageNumber`/`pageSize` |
| 重启后数据没了 | `persistence=none` 是进程内示例；持久化需要 MySQL 等实际实现 |

完整生成选项见 [生成器指南](../wuli3-ddd-generator.md)，依赖接入细节见 [BOM 指南](../wuli3-dependencies.md)，构建门禁与插件配置见 [构建约定指南](../wuli3-build-logic.md)。工具和扩展点的按场景示例从 [指南首页](../README.md) 继续阅读。
