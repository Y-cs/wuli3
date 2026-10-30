---
description: Gradle 质量门禁、BOM 消费验证和文档站构建方式。
---

# 构建与验证

Wuli3 把“代码能编译”和“发布物可被外部项目消费”分开验证。提交前先跑受影响模块，发布前再跑完整质量门禁和消费者验证。

## 常用命令

```bash
./gradlew test
./gradlew check
./gradlew clean check
./gradlew verifyBomConsumers
```

check 包含编译、JUnit、Checkstyle、Spotless、Forbidden APIs、Error Prone、NullAway、SpotBugs、JaCoCo 和适用模块的架构检查。核心生产代码禁止使用 java.util.Date、Calendar 和 java.sql 时间类型。

## 为什么要验证消费者

模块内测试从项目依赖解析，可能看不出 POM、BOM、Gradle Module Metadata 或传递依赖声明错误。verifyBomConsumers 会先发布到临时仓库，再用隔离的 Gradle 和 Maven 工程完成最小编译与测试。

发布顺序建议：

1. clean check。
2. verifyBomConsumers。
3. 需要真实设施时执行独立集成测试。
4. 通过后才发布正式制品。

## 文档模块

文档站位于 wuli3-docs，使用 VitePress 1.6.4 和锁定的 package-lock.json：

```bash
cd wuli3-docs
npm ci
npm run docs:build
```

Gradle 任务 wuli3-docs:checkDocs 会执行同一套安装和构建，输出到 wuli3-docs/build/docs。文档中的 Vue 图示使用原生组件，不额外引入 Mermaid 运行时。

## 失败时先看边界

依赖解析失败先确认仓库和 BOM；NullAway 失败先确认空安全契约；DDD 门禁失败先确认模块责任；消费者失败先检查发布元数据，不要用增加版本号或重复声明依赖掩盖根因。
