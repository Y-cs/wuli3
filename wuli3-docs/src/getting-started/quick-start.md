---
description: 用最小依赖生成、启动并验证一个 Wuli3 服务。
---

# 快速入门

这条路径先把一个没有数据库、消息和注册中心的服务跑起来。这样你能先看到 Wuli3 的请求、错误、分页和模块边界，再决定要接入哪些外部设施。

## 准备环境

- JDK 21。
- Git。
- 一个可以访问 Maven 仓库的环境。
- JBang（使用已发布生成器时）或当前仓库的 Gradle Wrapper。

Wuli3 当前版本是 `1.0.0`。它可能只存在于本地或团队仓库，不代表已经发布到 Maven Central。

## 准备可解析的制品

首次从本仓库体验时，在 **Wuli3 仓库根目录**执行：

```bash
./gradlew publishToMavenLocal :wuli3-build-logic:publishToMavenLocal
```

它将库、BOM、生成器和约定插件发布到本机 Maven 仓库。生成工程默认包含 `mavenLocal()`，可以解析本机制品。只发布 BOM 无法补齐 starter 和插件；首次下载依赖可能超过几分钟。

已有团队制品库的使用者可以跳过本地发布，但要确认上述四类制品均已存在。JBang 的 `--repos` 只控制生成器解析，不修改生成工程的仓库配置。

## 生成最小服务

在准备新工程的父目录执行：

```bash
jbang --repos mavenlocal com.kjs.wuli3:wuli3-ddd-generator:1.0.0 generate \
  --service demo-order --package com.example.order --domain order \
  --persistence none --messaging none --rpc none --registry none \
  --output .
```

`--output` 是父目录，最终工程会落在 `./demo-order/`。目标目录必须不存在或为空，生成器不会覆盖已有业务文件。

如果制品在内部仓库，将 `--repos mavenlocal` 替换为 `--repos <仓库地址>`；生成后仍需在自己的 `settings.gradle.kts` 中为 `pluginManagement.repositories` 和 `dependencyResolutionManagement.repositories` 配置插件仓库和依赖仓库。凭据从环境或本机配置注入。

未安装 JBang 时，可在 Wuli3 仓库根目录直接运行生成器；以下示例将工程生成到 `/tmp/wuli3-demo/demo-order`，此目录须不存在或为空：

```bash
./gradlew :wuli3-ddd-generator:run \
  --args="generate --service demo-order --package com.example.order --domain order --persistence none --messaging none --rpc none --registry none --output /tmp/wuli3-demo"
```

直接运行仅负责生成文件，生成工程的构建仍依赖上一节准备的库和插件。

## 构建并启动

```bash
cd demo-order
./gradlew check
./gradlew :demo-order-bootstrap:bootRun
```

默认业务端口为 `8080`，管理端口为 `8099`。模块目录与任务路径使用模块前缀（默认省略服务名末尾的 `-service`）；使用源码生成路线时先进入 `/tmp/wuli3-demo/demo-order`。`check` 验证编译、测试、格式、静态分析和 DDD 模块边界；生成模板默认关闭覆盖率阈值门禁和事务位置审查。

## 用 HTTP 看见一次完整闭环

```bash
curl -i -X POST http://localhost:8080/api/order \
  -H 'Content-Type: application/json' \
  -d '{"id":{"value":"order-1"},"status":"ACTIVE"}'

curl -i http://localhost:8080/api/order/order-1

curl -i -X POST http://localhost:8080/api/order/page \
  -H 'Content-Type: application/json' \
  -d '{"pageNumber":1,"pageSize":20,"sorts":[]}'

curl -i -X DELETE http://localhost:8080/api/order/order-1
```

保存相同标识会更新状态；分页页码从 `1` 开始；`persistence=none` 的数据只存在于当前进程。请求 `pageNumber=0` 应进入统一错误码响应。

成功响应的 `code` 为 `"0"`，保存后分页 `data.total` 应为 `1`；删除后再次分页应为 `0`。无中间件示例不保证事务回滚，也不生成真实业务规则。

## 第一次失败时检查哪里

| 现象 | 检查方向 |
| --- | --- |
| JBang 找不到生成器 | 是否完成本地发布、是否带 `--repos mavenlocal` |
| Gradle 找不到插件 | wuli3-build-logic 实现和 plugin marker 是否发布、插件仓库是否配置 |
| BOM 可解析但 starter 不可解析 | 是否仅发布了 BOM、库版本是否一致 |
| bootRun 找不到模块 | 模块名是否包含 `demo-order-` 前缀 |
| 保存请求绑定失败 | `id` 必须是含 `value` 的对象，状态使用 `ACTIVE` 或 `INACTIVE` |

## 你刚刚看到了什么

入口负责协议适配，应用服务负责完整用例，领域模型负责不变量，基础设施负责存储。成功响应、错误响应、请求标识和日志上下文由 Web/Core starter 协作完成。

接着阅读[设计总览](/concepts/overview)，再根据实际需求选择[接入已有项目](/getting-started/adoption)或[生成器与项目骨架](/getting-started/generator)。
