# wuli3-build-logic 使用指南

Gradle 构建逻辑模块，提供可复用、可发布的约定插件。

根工程通过 `settings.gradle.kts` 引入：

```kotlin
pluginManagement {
    includeBuild("wuli3-build-logic")
}
```

included build 名称只用于本仓库识别；业务模块不需要直接引用该名称。

提供约定插件：

- `com.kjs.wuli3.java-conventions`：JDK 21、测试、基础依赖，以及 Spotless、Palantir Java Format、Checkstyle、SpotBugs、Forbidden APIs、Error Prone、NullAway 质量门禁。
- `com.kjs.wuli3.kotlin-conventions`：Kotlin/JVM 2.4.20、JDK 21、Java/Kotlin 混编与 Lombok 约定。
- `com.kjs.wuli3.kotlin-spring-conventions`：在 Kotlin 约定上启用 Spring `all-open` 与 `kotlin-reflect`。
- `com.kjs.wuli3.java-spring-conventions`：Spring Boot starter 模块约定。
- `com.kjs.wuli3.publishing-conventions`：统一 Maven Publication 和临时发布仓库约定。

普通 Java 模块使用：

```kotlin
plugins {
    id("com.kjs.wuli3.java-conventions")
}
```

Spring Boot starter 模块使用：

```kotlin
plugins {
    id("com.kjs.wuli3.java-spring-conventions")
}
```

Kotlin 模块使用：

```kotlin
plugins {
    id("com.kjs.wuli3.kotlin-conventions")
}
```

需要 Spring 注解开放能力的 Kotlin 模块使用 `com.kjs.wuli3.kotlin-spring-conventions`。同一模块内的 Kotlin 编译任务先于 Java 编译任务，模块之间仍由 Gradle 项目依赖决定顺序。Kotlin 源码放在 `src/main/kotlin` 和 `src/test/kotlin`，格式化使用 Spotless + ktfmt，执行 `spotlessKotlinCheck` 或 `spotlessKotlinApply`。

业务模块只应用约定插件，不重复配置质量规则。

## 业务项目使用

发布到 Maven 仓库后，业务 Gradle 项目可以通过插件版本使用：

```kotlin
pluginManagement {
    repositories {
        maven("https://maven.example.com/repository/releases")
        gradlePluginPortal()
        mavenCentral()
    }
}
```

```kotlin
plugins {
    id("com.kjs.wuli3.java-conventions") version "1.0.0"
}
```

默认会导入 `com.kjs.wuli3:wuli3-dependencies:1.0.0` 作为 BOM，并启用 Spotless、Palantir Java Format、ktfmt、Checkstyle、SpotBugs、Forbidden APIs、Error Prone、NullAway 和 JaCoCo 覆盖率门禁。
Error Prone、NullAway、Lombok 和 JSpecify 的依赖版本统一由该 BOM 管理，质量配置不再重复指定版本。
自定义 BOM 需要导入 Wuli3 BOM 或提供这些版本约束；生产与测试的注解处理器使用同一 BOM。
业务项目可在 `gradle.properties` 覆盖默认值：

```properties
wuli3.conventions.bom-coordinates=com.kjs.wuli3:wuli3-dependencies:1.0.0
wuli3.conventions.java-version=21
wuli3.conventions.spotless.enabled=true
wuli3.conventions.palantir-java-format.version=2.97.0
wuli3.conventions.nullaway.annotated-packages=com.example.service
wuli3.conventions.nullaway.enabled=true
wuli3.conventions.spotbugs.enabled=true
wuli3.conventions.forbidden-apis.enabled=true
wuli3.conventions.forbidden-apis.test-enabled=false
wuli3.conventions.jacoco.verification.enabled=true
wuli3.conventions.jacoco.line.minimum=0.45
```

质量约定会将由 Dubbo 容器注入的 `@DubboReference` 字段加入 NullAway 的排除字段注解列表，避免把框架注入前的空值误报为未初始化字段。

Lombok 同时配置在生产与测试的编译期依赖、注解处理器路径上。`@Data`、`@Builder`、`@RequiredArgsConstructor` 等注解
生成的成员与手写代码一起参与编译；不能仅添加 `compileOnly`，也不要通过关闭 Error Prone 或 NullAway 处理兼容问题。
遇到 `package lombok does not exist` 应检查编译期依赖，找不到生成成员时应检查注解处理器；
`[NullAway]` 报错则需要核对空安全契约，Lombok 注解不等于允许手写代码返回空值。

Spotless 提供 `spotlessCheck` 和 `spotlessApply`，Java 格式化规则使用 Palantir Java Format。
折行和行长由格式化器统一决定，Checkstyle 不再额外设置硬行长限制；格式化器保留的长字符串等内容允许通过。
Java 包名沿用 Checkstyle 规则：首段仅含小写字母，后续各段以小写字母开头且仅含小写字母和数字，不允许下划线。

JaCoCo 默认按模块整体 `LINE`
覆盖率做门禁，并生成 HTML 和 XML 报告；XML 报告可供 CI、SonarQube 或 diff coverage 工具使用，但本约定插件本身不计算
提交级覆盖率。

本仓库通过以下属性继续使用本地 BOM 项目：

```properties
wuli3.conventions.use-project-bom=true
wuli3.conventions.project-bom-path=:wuli3-dependencies
```

## 发布到 Maven 仓库

本模块可以作为 Gradle 插件发布。默认坐标：

```text
com.kjs.wuli3:wuli3-build-logic:1.0.0
```

默认版本与主构建共用同一个定义，配置在仓库根目录 `gradle.properties`：

```properties
wuli3.version=1.0.0
```

构建约定插件时，Kotlin 插件依赖版本也从根目录 `gradle.properties` 读取：

```properties
wuli3.build.kotlin-version=2.4.20
```

未配置时默认使用 `2.4.20`。当前构建的同名 Gradle 属性优先于根配置，可通过
`-Pwuli3.build.kotlin-version=版本号` 覆盖。该值统一控制 Kotlin JVM、Lombok 和 Spring 插件依赖，
需要重新构建并发布约定插件才会影响消费方；它不控制 Gradle 内嵌 Kotlin DSL 编译器版本。

发布到本地 Maven 仓库：

```bash
./gradlew -p wuli3-build-logic publishToMavenLocal
```

发布到 central Maven 仓库：

```bash
./gradlew -p wuli3-build-logic publish \
  -PcentralSnapshotsUrl=https://maven.example.com/repository/snapshots \
  -PcentralReleasesUrl=https://maven.example.com/repository/releases \
  -PcentralUsername=your-username \
  -PcentralPassword=your-password
```

也可以使用环境变量提供凭据：

```bash
CENTRAL_MAVEN_USERNAME=your-username CENTRAL_MAVEN_PASSWORD=your-password ./gradlew -p wuli3-build-logic publish
```

Maven 项目不能直接使用 Gradle 插件。后续如需统一 Maven 构建规则，应单独提供 parent POM。
