package com.kjs.wuli3.ddd

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.ToolProvider

/**
 * 验证七模块声明与字节码依赖方向，以及共享内核和领域层的纯净边界。
 *
 * @author 国杨 create on 2026/10/14 18:08
 */
class DddConventionsPluginTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `pure kernel dependencies allow compile time lombok without logging`() {
        fixture("""
            dependencies { api("com.kjs.wuli3:wuli3-core:1.0"); compileOnly("org.jspecify:jspecify:1.0.0") }
        """.trimIndent())
        val result = runner("verifyModuleDependencies", "--configuration-cache").build()
        assertThat(result.output).contains("BUILD SUCCESSFUL")
        assertThat(runner("verifyModuleDependencies", "--configuration-cache").build().output)
            .contains("Reusing configuration cache")
    }

    @Test
    fun `kernel rejects lombok as runtime dependency`() {
        fixture("dependencies { runtimeOnly(\"org.projectlombok:lombok:1.18.42\") }")
        assertThat(runner("verifyModuleDependencies").buildAndFail().output)
            .contains("shared-kernel 禁止生产依赖", "runtimeClasspath: org.projectlombok:lombok")
    }

    @Test
    fun `kernel accepts lombok generated annotation in bytecode`() {
        fixture(bytecodeConfiguration())
        compile("lombok/Generated.java", "package lombok; @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) public @interface Generated {}")
        compile("example/Value.java", "package example; public class Value { @lombok.Generated public String value() { return \"value\"; } }")
        Files.delete(directory.resolve("bytecode/lombok/Generated.class"))
        assertThat(runner("verifyTypeBoundaries").build().output).contains("BUILD SUCCESSFUL")
    }

    @Test
    fun `spring annotation processor dependency also fails`() {
        fixture("dependencies { annotationProcessor(\"org.springframework:spring-context:7.0.0\") }")
        val result = runner("verifyModuleDependencies").buildAndFail()
        assertThat(result.output).contains("shared-kernel 禁止生产依赖", "org.springframework:spring-context")
    }

    @Test
    fun `spring production dependency fails even when no code references it`() {
        fixture("dependencies { implementation(\"org.springframework:spring-context:7.0.0\") }")
        val result = runner("verifyModuleDependencies").buildAndFail()
        assertThat(result.output).contains("shared-kernel 禁止生产依赖", "org.springframework:spring-context")
    }

    @Test
    fun `legal JDK types pass bytecode verification`() {
        fixture(bytecodeConfiguration())
        compile("example/Pure.java", """
            package example;
            public class Pure {
                public static final long ID = 123L;
                public static final double RATIO = 0.5;
                public String metadata = "org.springframework.context.Context";
                public int[] numbers;
                public String[][] names;
                public javax.xml.namespace.QName qualifiedName;
                public org.w3c.dom.Document document;
                public java.time.Instant now() { return java.time.Instant.now(); }
            }
        """.trimIndent())
        compile("example/package-info.java", "@Deprecated package example;")
        assertThat(runner("verifyTypeBoundaries").build().output).contains("BUILD SUCCESSFUL")
    }

    @Test
    fun `kernel rejects arrays of external types and non JDK javax packages`() {
        fixture(bytecodeConfiguration())
        compile("javax/external/Value.java", "package javax.external; public class Value {}")
        compile("example/Impure.java", "package example; public class Impure { public javax.external.Value[][] values; }")
        Files.delete(directory.resolve("bytecode/javax/external/Value.class"))
        assertThat(runner("verifyTypeBoundaries").buildAndFail().output)
            .contains("shared-kernel 禁止类型引用", "javax.external.Value")
    }

    @ParameterizedTest
    @ValueSource(strings = ["cast", "array", "multi-array"])
    fun `domain rejects instruction only Spring references`(instruction: String) {
        fixture(bytecodeConfiguration(), "domain")
        compile("org/springframework/context/Context.java", "package org.springframework.context; public class Context {}")
        val expression = when (instruction) {
            "cast" -> "(org.springframework.context.Context) value"
            "array" -> "new org.springframework.context.Context[1]"
            else -> "new org.springframework.context.Context[1][1]"
        }
        compile("example/domain/Impure.java", """
            package example.domain;
            public class Impure { public Object execute(Object value) { return $expression; } }
        """.trimIndent())
        Files.delete(directory.resolve("bytecode/org/springframework/context/Context.class"))
        assertThat(runner("verifyTypeBoundaries").buildAndFail().output)
            .contains("domain 禁止 Spring 类型引用", "org.springframework.context.Context")
    }

    @ParameterizedTest
    @ValueSource(strings = ["invocation-descriptor", "generic-array", "catch"])
    fun `domain rejects Spring references in descriptors signatures and catch clauses`(reference: String) {
        fixture(bytecodeConfiguration(), "domain")
        compile("org/springframework/context/Context.java", "package org.springframework.context; public class Context extends RuntimeException {}")
        compile("com/kjs/wuli3/core/Factory.java", """
            package com.kjs.wuli3.core;
            public class Factory { public static org.springframework.context.Context create() { return null; } }
        """.trimIndent())
        val member = when (reference) {
            "invocation-descriptor" -> "public Object execute() { return com.kjs.wuli3.core.Factory.create(); }"
            "generic-array" -> "public java.util.List<org.springframework.context.Context>[] values;"
            else -> "public Object execute() { try { throw new RuntimeException(); } catch (org.springframework.context.Context error) { return error; } }"
        }
        compile("example/domain/Impure.java", "package example.domain; public class Impure { $member }")
        Files.delete(directory.resolve("bytecode/org/springframework/context/Context.class"))
        Files.delete(directory.resolve("bytecode/com/kjs/wuli3/core/Factory.class"))
        assertThat(runner("verifyTypeBoundaries").buildAndFail().output)
            .contains("domain 禁止 Spring 类型引用", "org.springframework.context.Context")
    }

    @ParameterizedTest
    @ValueSource(strings = ["verifyTypeBoundaries", "verifyTransactionBoundaries"])
    fun `architecture gates fail if a production class cannot be imported`(task: String) {
        transactionFixture(enabled = true)
        val build = directory.resolve("build.gradle.kts")
        Files.writeString(build, Files.readString(build) + "\n" + bytecodeConfiguration())
        val broken = directory.resolve("bytecode/example/app/Broken.class")
        Files.createDirectories(broken.parent)
        Files.write(broken, byteArrayOf(0, 1, 2, 3))
        assertThat(runner(task).buildAndFail().output)
            .contains("无法完整导入生产类", "example.app.Broken")
        assertThat(directory.resolve("build/reports/ddd/$task.txt")).doesNotExist()
    }

    @Test
    fun `spring type references fail even without a dependency declaration`() {
        fixture(bytecodeConfiguration())
        compile("org/springframework/context/Context.java", "package org.springframework.context; public class Context {}")
        compile("example/Impure.java", "package example; public class Impure { public Object context() { return new org.springframework.context.Context(); } }")
        Files.delete(directory.resolve("bytecode/org/springframework/context/Context.class"))
        val result = runner("verifyTypeBoundaries").buildAndFail()
        assertThat(result.output).contains("shared-kernel 禁止类型引用", "org.springframework.context.Context")
    }

    @Test
    fun `check includes all architecture gates`() {
        fixture("")
        val result = runner("check", "--dry-run").build()
        assertThat(result.output).contains(":verifyModuleDependencies SKIPPED", ":verifyTypeBoundaries SKIPPED", ":verifyTransactionBoundaries SKIPPED")
    }

    @Test
    fun `infra allows only query contracts dto and domain references`() {
        fixture(bytecodeConfiguration(), "infra")
        listOf("app.repository.OrderQueryRepository", "app.query.OrderQuery", "app.dto.OrderDTO", "domain.gateway.CustomerGateway", "api.dto.ResultDto", "domain.Order").forEachIndexed { index, name ->
            compileReference(name, "Allowed$index")
        }
        assertThat(runner("verifyTypeBoundaries").build().output).contains("BUILD SUCCESSFUL")
    }

    @Test
    fun `infra rejects application implementations facade adapter and unknown application packages`() {
        fixture(bytecodeConfiguration(), "infra")
        listOf("app.service.Service", "app.facade.FacadeImpl", "api.facade.Facade", "adapter.api.Api", "app.other.Other", "app.acl.Gateway", "app.repository.Reader", "app.query.Criteria", "app.dto.Result", "app.dto.OrderDto").forEachIndexed { index, name ->
            compileReference(name, "Forbidden$index")
        }
        val output = runner("verifyTypeBoundaries").buildAndFail().output
        assertThat(output).contains("infra 引用 app 类型必须以", "app.acl.Gateway", "app.dto.OrderDto", "app.service.Service", "app.facade.FacadeImpl", "api.facade.Facade", "adapter.api.Api", "app.other.Other")
    }

    @Test
    fun `infra app reference suffixes are configurable`() {
        fixture(bytecodeConfiguration(), "infra")
        Files.writeString(directory.resolve("gradle.properties"), Files.readString(directory.resolve("gradle.properties")) +
            "\nwuli3.ddd.types.infra-app-allowed-suffixes=Projection")
        compileReference("app.read.OrderProjection", "Reference")
        assertThat(runner("verifyTypeBoundaries").build().output).contains("BUILD SUCCESSFUL")
    }

    @ParameterizedTest
    @ValueSource(strings = ["app", "api", "domain", "adapter", "bootstrap", "shared-kernel"])
    fun `external service API declarations are restricted to infra`(role: String) {
        fixture("dependencies { implementation(\"external.customer:customer-api:1.0\") }", role)
        appendProperties("wuli3.ddd.external-api.coordinates=external.customer:customer-api")
        assertThat(runner("verifyModuleDependencies").buildAndFail().output)
            .contains("外部服务 API 只能由 infra 引入", "external.customer:customer-api")
    }

    @Test
    fun `infra accepts declared external APIs and app accepts unrelated libraries`() {
        fixture("dependencies { implementation(\"external.customer:customer-api:1.0\") }", "infra")
        appendProperties("wuli3.ddd.external-api.coordinates=external.customer:customer-api")
        assertThat(runner("verifyModuleDependencies").build().output).contains("BUILD SUCCESSFUL")
        fixture("dependencies { implementation(\"other:some-api:1.0\") }", "app")
        appendProperties("wuli3.ddd.external-api.coordinates=external.customer:customer-api")
        assertThat(runner("verifyModuleDependencies").build().output).contains("BUILD SUCCESSFUL")
    }

    /** 生成工程使用 `<service>-<角色>` 模块名时，依赖门禁仍按角色生效。 */
    @Test
    fun `service prefixed module names keep role gates`() {
        fixture("dependencies { implementation(\"external.customer:customer-api:1.0\") }", "app")
        Files.writeString(directory.resolve("settings.gradle.kts"), "rootProject.name = \"order-service-app\"")
        appendProperties("wuli3.ddd.external-api.coordinates=external.customer:customer-api")
        assertThat(runner("verifyModuleDependencies").buildAndFail().output)
            .contains("外部服务 API 只能由 infra 引入", "order-service-app")
    }

    /** 带服务名前缀的共享内核仍按纯净内核应用依赖门禁。 */
    @Test
    fun `service prefixed kernel keeps purity gate`() {
        fixture("dependencies { implementation(\"org.springframework:spring-context:7.0.0\") }", "shared-kernel")
        Files.writeString(
            directory.resolve("settings.gradle.kts"), "rootProject.name = \"order-service-shared-kernel\"")
        assertThat(runner("verifyModuleDependencies").buildAndFail().output)
            .contains("shared-kernel 禁止生产依赖", "org.springframework:spring-context")
    }

    @ParameterizedTest
    @ValueSource(strings = ["app", "api", "domain", "adapter", "bootstrap", "shared-kernel", "infra"])
    fun `external API bytecode references only pass in infra`(role: String) {
        fixture(bytecodeConfiguration(), role)
        appendProperties("wuli3.ddd.external-api.packages=external.customer.api")
        compile("external/customer/api/Customer.java", "package external.customer.api; public class Customer {}")
        compile("example/$role/Client.java".replace("shared-kernel", "sharedkernel"),
            "package example.${role.replace("shared-kernel", "sharedkernel")}; public class Client { public external.customer.api.Customer value; }")
        Files.delete(directory.resolve("bytecode/external/customer/api/Customer.class"))
        if (role == "infra") {
            assertThat(runner("verifyTypeBoundaries").build().output).contains("BUILD SUCCESSFUL")
        } else {
            assertThat(runner("verifyTypeBoundaries").buildAndFail().output)
                .contains("外部服务 API 类型只能由 infra 引用", "external.customer.api.Customer")
        }
    }

    @Test
    fun `external API prefixes do not match sibling packages or local API`() {
        fixture(bytecodeConfiguration(), "app")
        appendProperties("wuli3.ddd.external-api.packages=external.customer.api")
        compileReference("api.dto.OrderDTO", "Local", "app")
        compileReference("api2.Customer", "Sibling", "app", "external.customer")
        assertThat(runner("verifyTypeBoundaries").build().output).contains("BUILD SUCCESSFUL")
    }

    @Test
    fun `app acl declarations and references are forbidden even with configured suffix`() {
        fixture(bytecodeConfiguration(), "app")
        compile("example/app/acl/CustomerAcl.java", "package example.app.acl; public interface CustomerAcl {}")
        assertThat(runner("verifyTypeBoundaries").buildAndFail().output)
            .contains("禁止 app.acl", "domain.gateway", "XxxGateway", "infra.gateway", "XxxGatewayImpl")
        Files.delete(directory.resolve("bytecode/example/app/acl/CustomerAcl.class"))
        fixture(bytecodeConfiguration(), "infra")
        appendProperties("wuli3.ddd.types.infra-app-allowed-suffixes=Acl")
        compileReference("app.acl.CustomerAcl", "Client")
        assertThat(runner("verifyTypeBoundaries").buildAndFail().output)
            .contains("禁止引用 app.acl", "domain.gateway", "XxxGateway", "infra.gateway", "XxxGatewayImpl")
    }

    private fun appendProperties(value: String) {
        val properties = directory.resolve("gradle.properties")
        Files.writeString(properties, Files.readString(properties) + "\n" + value)
    }

    @Test
    fun `application query repositories must be interfaces`() {
        fixture(bytecodeConfiguration(), "app")
        compile("example/app/repository/OrderQueryRepository.java", "package example.app.repository; public interface OrderQueryRepository {}")
        assertThat(runner("verifyTypeBoundaries").build().output).contains("BUILD SUCCESSFUL")
        compile("example/app/repository/Bad.java", "package example.app.repository; public class Bad {}")
        assertThat(runner("verifyTypeBoundaries").buildAndFail().output).contains("app.repository 只允许查询仓储接口", "example.app.repository.Bad")
    }

    @Test
    fun `kernel rejects all business project dependencies`() {
        fixture("")
        val modules = listOf("domain", "api", "app", "infra", "adapter", "bootstrap")
        Files.writeString(directory.resolve("settings.gradle.kts"),
            "rootProject.name = \"shared-kernel\"\n" + modules.joinToString("\n") { "include(\"$it\")" })
        modules.forEach { Files.createDirectories(directory.resolve(it)) }
        val build = directory.resolve("build.gradle.kts")
        Files.writeString(build, Files.readString(build) + "\ndependencies {\n" +
            modules.joinToString("\n") { "implementation(project(\":$it\"))" } + "\n}")
        val output = runner("verifyModuleDependencies").buildAndFail().output
        modules.forEach { assertThat(output).contains(it) }
    }

    @ParameterizedTest
    @CsvSource(
        "domain, shared-kernel, api|app|infra|adapter|bootstrap",
        "api, shared-kernel, domain|app|infra|adapter|bootstrap",
        "app, shared-kernel|domain|api, infra|adapter|bootstrap",
        "infra, shared-kernel|domain|api|app, adapter|bootstrap",
        "adapter, shared-kernel|api|app|domain, infra|bootstrap",
        "bootstrap, shared-kernel|domain|api|app|infra|adapter, none"
    )
    fun `project dependencies follow module DAG`(role: String, allowed: String, forbidden: String) {
        fixture("", role)
        val modules = (allowed.split('|') + forbidden.split('|')).filter { it != "none" }
        Files.writeString(directory.resolve("settings.gradle.kts"),
            "rootProject.name = \"$role\"\n" + modules.joinToString("\n") { "include(\"$it\")" })
        modules.forEach { Files.createDirectories(directory.resolve(it)) }
        val build = directory.resolve("build.gradle.kts")
        val original = Files.readString(build)
        Files.writeString(build, original + "\ndependencies {\n" +
            allowed.split('|').joinToString("\n") { "implementation(project(\":$it\"))" } + "\n}")
        assertThat(runner("verifyModuleDependencies").build().output).contains("BUILD SUCCESSFUL")
        if (forbidden != "none") {
            Files.writeString(build, original + "\ndependencies {\n" +
                forbidden.split('|').joinToString("\n") { "implementation(project(\":$it\"))" } + "\n}")
            val output = runner("verifyModuleDependencies").buildAndFail().output
            forbidden.split('|').forEach { assertThat(output).contains(it) }
        }
    }

    @ParameterizedTest
    @CsvSource(
        "domain, api.dto.OrderDto|app.service.Service|infra.Storage|adapter.Api|bootstrap.Main",
        "api, domain.Order|app.service.Service|infra.Storage|adapter.Api|bootstrap.Main",
        "app, infra.Storage|adapter.Api|bootstrap.Main",
        "adapter, infra.Storage|bootstrap.Main"
    )
    fun `bytecode rejects reverse dependencies even without declarations`(role: String, forbidden: String) {
        fixture(bytecodeConfiguration(), role)
        forbidden.split('|').forEachIndexed { index, name -> compileReference(name, "Forbidden$index", role) }
        val output = runner("verifyTypeBoundaries").buildAndFail().output
        forbidden.split('|').forEach { assertThat(output).contains("example.$it") }
    }

    @Test
    fun `domain forbids spring declarations and method body references`() {
        fixture("dependencies { implementation(\"org.springframework:spring-context:7.0.0\") }", "domain")
        assertThat(runner("verifyModuleDependencies").buildAndFail().output).contains("org.springframework:spring-context")
        fixture(bytecodeConfiguration(), "domain")
        compile("org/springframework/context/Context.java", "package org.springframework.context; public class Context {}")
        compile("example/domain/Impure.java", "package example.domain; public class Impure { public Object context() { return new org.springframework.context.Context(); } }")
        Files.delete(directory.resolve("bytecode/org/springframework/context/Context.class"))
        assertThat(runner("verifyTypeBoundaries").buildAndFail().output).contains("org.springframework.context.Context")
    }

    @Test
    fun `independent base package overrides unrelated nullaway packages`() {
        fixture(bytecodeConfiguration(), "infra")
        Files.writeString(directory.resolve("gradle.properties"),
            "wuli3.ddd.base-package=custom.business\nwuli3.conventions.nullaway.annotated-packages=unrelated,another")
        compileReference("app.service.Service", "Forbidden", basePackage = "custom.business")
        assertThat(runner("verifyTypeBoundaries").buildAndFail().output).contains("custom.business.app.service.Service")
    }

    @ParameterizedTest
    @ValueSource(strings = ["example,other", "example;other", "example other", "example..other", ""])
    fun `invalid architecture base package fails even with no classes`(basePackage: String) {
        fixture(bytecodeConfiguration(), "infra")
        Files.writeString(directory.resolve("gradle.properties"), "wuli3.ddd.base-package=$basePackage")
        assertThat(runner("verifyTypeBoundaries").buildAndFail().output).contains("必须是单一有效基础包")
    }

    @ParameterizedTest
    @CsvSource(
        "domain, sharedkernel.Id",
        "api, sharedkernel.Id",
        "app, domain.Order|api.dto.OrderDTO|sharedkernel.Id",
        "adapter, app.service.OrderService|api.dto.OrderDTO|domain.Order",
        "bootstrap, adapter.Api|infra.Storage|app.service.OrderService|domain.Order|api.dto.OrderDTO"
    )
    fun `bytecode allows forward module dependencies`(role: String, allowed: String) {
        fixture(bytecodeConfiguration(), role)
        allowed.split('|').forEachIndexed { index, name -> compileReference(name, "Allowed$index", role) }
        assertThat(runner("verifyTypeBoundaries").build().output).contains("BUILD SUCCESSFUL")
    }

    @Test
    fun `architecture gates inspect production output only`() {
        transactionFixture(enabled = true)
        // 使用插件默认输出接线；测试目录里的违规类不能进入生产架构检查。
        fixture("", "app")
        appendProperties("wuli3.ddd.base-package=example\nwuli3.ddd.transaction-review.enabled=true")
        compileBatch(mapOf(
            "example/app/repository/InvalidRepository.java" to
                "package example.app.repository; public class InvalidRepository {}",
            "example/app/facade/InvalidFacade.java" to
                "package example.app.facade; @org.springframework.transaction.annotation.Transactional public class InvalidFacade {}"
        ))
        val output = directory.resolve("build/classes/java")
        Files.createDirectories(output)
        Files.move(directory.resolve("bytecode"), output.resolve("test"))
        compile("example/app/service/OrderService.java", "package example.app.service; public class OrderService {}")
        Files.move(directory.resolve("bytecode"), output.resolve("main"))
        val result = runner("verifyTypeBoundaries", "verifyTransactionBoundaries").build()
        assertThat(result.task(":verifyTypeBoundaries")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.task(":verifyTransactionBoundaries")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `architecture gates allow empty classes and unmatched selections`(hasClasses: Boolean) {
        fixture(bytecodeConfiguration() + """

            tasks.named<com.kjs.wuli3.ddd.VerifyTransactionBoundaries>("verifyTransactionBoundaries") {
                classes.setFrom(files("bytecode"))
            }
        """.trimIndent(), "app")
        appendProperties("wuli3.ddd.base-package=example\nwuli3.ddd.transaction-review.enabled=true")
        if (hasClasses) {
            compile("example/app/service/PlainService.java", "package example.app.service; public class PlainService {}")
        }
        val result = runner("verifyTypeBoundaries", "verifyTransactionBoundaries").build()
        assertThat(result.task(":verifyTypeBoundaries")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.task(":verifyTransactionBoundaries")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `type and transaction gates reuse configuration cache during execution`() {
        transactionFixture(enabled = true)
        val build = directory.resolve("build.gradle.kts")
        Files.writeString(build, Files.readString(build) + "\n" + bytecodeConfiguration())
        compile("example/app/service/OrderService.java", "package example.app.service; public class OrderService {}")
        val tasks = arrayOf("verifyTypeBoundaries", "verifyTransactionBoundaries")
        val first = runner(*tasks, "--configuration-cache", "--no-build-cache").build()
        tasks.forEach {
            assertThat(first.task(":$it")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
            Files.delete(directory.resolve("build/reports/ddd/$it.txt"))
        }
        val second = runner(*tasks, "--configuration-cache", "--no-build-cache").build()
        assertThat(second.output).contains("Reusing configuration cache")
        tasks.forEach { assertThat(second.task(":$it")!!.outcome).isEqualTo(TaskOutcome.SUCCESS) }
    }

    @Test
    fun `transaction review does not expand inherited or composed annotations`() {
        transactionFixture(enabled = true)
        compileBatch(mapOf(
            "example/app/service/BaseService.java" to """
                package example.app.service;
                @org.springframework.transaction.annotation.Transactional
                public class BaseService {
                    @org.springframework.transaction.annotation.Transactional public void inherited() {}
                    @org.springframework.transaction.annotation.Transactional public void overridden() {}
                }
            """.trimIndent(),
            "example/app/service/ComposedTransaction.java" to """
                package example.app.service;
                @org.springframework.transaction.annotation.Transactional
                @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
                @java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE, java.lang.annotation.ElementType.METHOD})
                public @interface ComposedTransaction {}
            """.trimIndent(),
            "example/app/facade/InheritedFacade.java" to """
                package example.app.facade;
                public class InheritedFacade extends example.app.service.BaseService {
                    @Override public void overridden() {}
                }
            """.trimIndent(),
            "example/app/facade/ComposedFacade.java" to """
                package example.app.facade;
                @example.app.service.ComposedTransaction
                public class ComposedFacade {
                    @example.app.service.ComposedTransaction public void execute() {}
                }
            """.trimIndent()
        ))
        assertThat(runner("verifyTransactionBoundaries").build().output).contains("BUILD SUCCESSFUL")
    }

    @Test
    fun `transaction review is disabled by default`() {
        transactionFixture(enabled = false)
        compile("example/app/facade/Facade.java", "package example.app.facade; @org.springframework.transaction.annotation.Transactional public class Facade {}")
        assertThat(runner("verifyTransactionBoundaries").build().output).contains("BUILD SUCCESSFUL")
    }

    @Test
    fun `transaction review accepts service class method and unannotated service`() {
        transactionFixture(enabled = true)
        compile("example/app/service/ClassService.java", "package example.app.service; @org.springframework.transaction.annotation.Transactional public class ClassService {}")
        compile("example/app/service/MethodService.java", "package example.app.service; public class MethodService { @org.springframework.transaction.annotation.Transactional public void execute() {} }")
        compile("example/app/service/PlainService.java", "package example.app.service; public class PlainService {}")
        assertThat(runner("verifyTransactionBoundaries").build().output).contains("BUILD SUCCESSFUL")
    }

    @ParameterizedTest
    @ValueSource(strings = ["class", "method"])
    fun `transaction review rejects facade annotations`(placement: String) {
        transactionFixture(enabled = true)
        val annotation = "@org.springframework.transaction.annotation.Transactional"
        val source = if (placement == "class")
            "package example.app.facade; $annotation public class Facade {}"
        else "package example.app.facade; public class Facade { $annotation public void execute() {} }"
        compile("example/app/facade/Facade.java", source)
        assertThat(runner("verifyTransactionBoundaries").buildAndFail().output).contains("example.app.facade.Facade")
    }

    @Test
    fun `transaction annotation type reference alone does not count as declaration`() {
        transactionFixture(enabled = true)
        compile("example/app/facade/Metadata.java", "package example.app.facade; public class Metadata { public Class<?> type() { return org.springframework.transaction.annotation.Transactional.class; } }")
        assertThat(runner("verifyTransactionBoundaries").build().output).contains("BUILD SUCCESSFUL")
    }

    @ParameterizedTest
    @CsvSource("OrderRepositoryImpl, class", "OrderRepositoryImpl, method", "OrderQueryRepositoryImpl, class", "OrderQueryRepositoryImpl, method")
    fun `transaction review allows infra repository classes and methods`(name: String, placement: String) {
        transactionFixture(enabled = true, role = "infra")
        val annotation = "@org.springframework.transaction.annotation.Transactional"
        val source = if (placement == "class")
            "package example.infra.persistence.order; $annotation public class $name {}"
        else "package example.infra.persistence.order; public class $name { $annotation public void execute() {} }"
        compile("example/infra/persistence/order/$name.java", source)
        assertThat(runner("verifyTransactionBoundaries").build().output).contains("BUILD SUCCESSFUL")
    }

    @ParameterizedTest
    @CsvSource(
        "persistence.order.mapper.OrderMapper, class",
        "config.OrderRepositoryImpl, method",
        "gateway.OrderRepositoryImpl, class",
        "gateway.CustomerGatewayImpl, method",
        "persistence.order.OrderStore, method",
        "persistence.order.OrderRepositoryImplHelper, class"
    )
    fun `transaction review rejects non repository infra declarations`(type: String, placement: String) {
        transactionFixture(enabled = true, role = "infra")
        val full = "example.infra.$type"
        val packageName = full.substringBeforeLast('.')
        val name = full.substringAfterLast('.')
        val annotation = "@org.springframework.transaction.annotation.Transactional"
        val source = if (placement == "class")
            "package $packageName; $annotation public class $name {}"
        else "package $packageName; public class $name { $annotation public void execute() {} }"
        compile(full.replace('.', '/') + ".java", source)
        assertThat(runner("verifyTransactionBoundaries").buildAndFail().output)
            .contains("DDD 事务边界违规", full)
    }

    @Test
    fun `transaction review allows configured package`() {
        transactionFixture(enabled = true)
        Files.writeString(directory.resolve("gradle.properties"), Files.readString(directory.resolve("gradle.properties")) +
            "\nwuli3.ddd.transaction-review.allowed-packages=app.service,app.facade,infra.persistence")
        compile("example/app/facade/OrderFacade.java", "package example.app.facade; @org.springframework.transaction.annotation.Transactional public class OrderFacade {}")
        assertThat(runner("verifyTransactionBoundaries").build().output).contains("BUILD SUCCESSFUL")
    }

    @Test
    fun `transaction review allows configured infrastructure suffix`() {
        transactionFixture(enabled = true, role = "infra")
        Files.writeString(directory.resolve("gradle.properties"), Files.readString(directory.resolve("gradle.properties")) +
            "\nwuli3.ddd.transaction-review.allowed-infrastructure-type-suffixes=Store")
        compile("example/infra/persistence/OrderStore.java", "package example.infra.persistence; @org.springframework.transaction.annotation.Transactional public class OrderStore {}")
        assertThat(runner("verifyTransactionBoundaries").build().output).contains("BUILD SUCCESSFUL")
    }

    @Test
    fun `all gates reuse unchanged reports and restore reports from build cache`() {
        transactionFixture(enabled = true)
        val buildFile = directory.resolve("build.gradle.kts")
        Files.writeString(buildFile, Files.readString(buildFile) + "\n" + bytecodeConfiguration())
        val settings = directory.resolve("settings.gradle.kts")
        Files.writeString(settings, Files.readString(settings) +
            "\nbuildCache { local { directory = file(\"local-build-cache\") } }")
        compile("example/app/service/OrderService.java", "package example.app.service; public class OrderService {}")
        val tasks = arrayOf("verifyModuleDependencies", "verifyTypeBoundaries", "verifyTransactionBoundaries")
        val first = runner(*tasks, "--build-cache").build()
        tasks.forEach { assertThat(first.task(":$it")!!.outcome).isEqualTo(TaskOutcome.SUCCESS) }
        val second = runner(*tasks, "--build-cache").build()
        tasks.forEach {
            assertThat(second.task(":$it")!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            Files.delete(directory.resolve("build/reports/ddd/$it.txt"))
        }
        val restored = runner(*tasks, "--build-cache").build()
        tasks.forEach { assertThat(restored.task(":$it")!!.outcome).isEqualTo(TaskOutcome.FROM_CACHE) }
        compile("example/app/facade/Invalid.java", "package example.app.facade; @org.springframework.transaction.annotation.Transactional public class Invalid {}")
        assertThat(runner(*tasks, "--build-cache").buildAndFail().output)
            .contains("DDD 事务边界违规", "example.app.facade.Invalid")
        assertThat(directory.resolve("build/reports/ddd/verifyTransactionBoundaries.txt")).doesNotExist()
    }

    @Test
    fun `bytecode and architecture configuration changes invalidate cached verdict`() {
        fixture(bytecodeConfiguration(), "infra")
        // 隔离测试缓存，确保首次执行不会复用其他运行的检查结果。
        Files.writeString(directory.resolve("settings.gradle.kts"),
            Files.readString(directory.resolve("settings.gradle.kts")) +
                "\nbuildCache { local { directory = file(\"local-build-cache\") } }")
        compileReference("app.service.Service", "Reference")
        val properties = directory.resolve("gradle.properties")
        Files.writeString(properties, "wuli3.ddd.base-package=unrelated")
        assertThat(runner("verifyTypeBoundaries", "--build-cache").build().task(":verifyTypeBoundaries")!!.outcome)
            .isEqualTo(TaskOutcome.SUCCESS)
        Files.writeString(properties, "wuli3.ddd.base-package=example")
        assertThat(runner("verifyTypeBoundaries", "--build-cache").buildAndFail().output)
            .contains("example.app.service.Service")
        val build = directory.resolve("build.gradle.kts")
        val original = Files.readString(build)
        Files.writeString(build, original + "\ntasks.named<com.kjs.wuli3.ddd.VerifyTypeBoundaries>(\"verifyTypeBoundaries\") { role.set(\"bootstrap\") }")
        assertThat(runner("verifyTypeBoundaries", "--build-cache").build().task(":verifyTypeBoundaries")!!.outcome)
            .isEqualTo(TaskOutcome.SUCCESS)
        Files.writeString(build, original)
        assertThat(runner("verifyTypeBoundaries", "--build-cache").buildAndFail().output)
            .contains("example.app.service.Service")
    }

    @Test
    fun `transaction inspection distinguishes declarations among more than one hundred classes`() {
        transactionFixture(enabled = true)
        val annotation = "@org.springframework.transaction.annotation.Transactional"
        val sources = (0 until 101).associate { index ->
            val name = "Reference" + index.toString().padStart(3, '0')
            "example/app/facade/$name.java" to
                "package example.app.facade; public class $name { public Class<?> type() { return org.springframework.transaction.annotation.Transactional.class; } }"
        }.toMutableMap()
        sources["example/app/facade/ZClassViolation.java"] =
            "package example.app.facade; $annotation public class ZClassViolation {}"
        sources["example/app/facade/ZMethodViolation.java"] =
            "package example.app.facade; public class ZMethodViolation { $annotation public void execute() {} }"
        compileBatch(sources)
        val output = runner("verifyTransactionBoundaries").buildAndFail().output
        assertThat(output).contains("example.app.facade.ZClassViolation", "example.app.facade.ZMethodViolation")
            .doesNotContain("DDD 事务边界违规：example.app.facade.Reference")
        Files.delete(directory.resolve("bytecode/example/app/facade/ZClassViolation.class"))
        Files.delete(directory.resolve("bytecode/example/app/facade/ZMethodViolation.class"))
        assertThat(runner("verifyTransactionBoundaries").build().output).contains("BUILD SUCCESSFUL")
    }

    @Test
    fun `repository inspection rejects concrete types among more than one hundred interfaces`() {
        fixture(bytecodeConfiguration(), "app")
        val sources = (0 until 101).associate { index ->
            val name = "Order" + index.toString().padStart(3, '0') + "QueryRepository"
            "example/app/repository/$name.java" to "package example.app.repository; public interface $name {}"
        }.toMutableMap()
        sources["example/app/repository/ZBad.java"] = "package example.app.repository; public class ZBad {}"
        compileBatch(sources)
        assertThat(runner("verifyTypeBoundaries").buildAndFail().output)
            .contains("app.repository 只允许查询仓储接口", "example.app.repository.ZBad")
        Files.delete(directory.resolve("bytecode/example/app/repository/ZBad.class"))
        assertThat(runner("verifyTypeBoundaries").build().output).contains("BUILD SUCCESSFUL")
    }

    private fun compileBatch(sources: Map<String, String>) {
        val files = sources.map { (path, source) ->
            val file = directory.resolve("sources/$path")
            Files.createDirectories(file.parent)
            Files.writeString(file, source)
            file.toString()
        }
        val classes = directory.resolve("bytecode")
        Files.createDirectories(classes)
        val arguments = listOf("-classpath", classes.toString(), "-d", classes.toString()) + files
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null, *arguments.toTypedArray())).isZero()
    }

    private fun transactionFixture(enabled: Boolean, role: String = "app") {
        fixture("""
            tasks.named<com.kjs.wuli3.ddd.VerifyTransactionBoundaries>("verifyTransactionBoundaries") {
                classes.setFrom(files("bytecode"))
            }
        """.trimIndent(), role)
        Files.writeString(directory.resolve("gradle.properties"),
            "wuli3.ddd.base-package=example\nwuli3.ddd.transaction-review.enabled=$enabled")
        compile("org/springframework/transaction/annotation/Transactional.java", """
            package org.springframework.transaction.annotation;
            @java.lang.annotation.Inherited
            @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
            @java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE, java.lang.annotation.ElementType.METHOD})
            public @interface Transactional {}
        """.trimIndent())
    }

    private fun compileReference(name: String, owner: String, role: String = "infra", basePackage: String = "example") {
        val full = "$basePackage.$name"
        compile(full.replace('.', '/') + ".java", "package ${full.substringBeforeLast('.')}; public class ${full.substringAfterLast('.')} {}")
        compile("${basePackage.replace('.', '/')}/$role/$owner.java", "package $basePackage.$role; public class $owner { public $full value; }")
        Files.delete(directory.resolve("bytecode/" + full.replace('.', '/') + ".class"))
    }

    private fun bytecodeConfiguration() = """
        tasks.named<com.kjs.wuli3.ddd.VerifyTypeBoundaries>("verifyTypeBoundaries") {
            classes.setFrom(files("bytecode"))
        }
    """.trimIndent()

    private fun fixture(extra: String, role: String = "shared-kernel") {
        Files.writeString(directory.resolve("gradle.properties"), "wuli3.conventions.nullaway.annotated-packages=example")
        Files.writeString(directory.resolve("settings.gradle.kts"), "rootProject.name = \"$role\"")
        Files.writeString(directory.resolve("build.gradle.kts"), """
            plugins { id("com.kjs.wuli3.ddd-conventions"); id("com.kjs.wuli3.java-conventions") }
            $extra
        """.trimIndent())
    }

    private fun compile(path: String, source: String) {
        val file = directory.resolve("sources/$path")
        Files.createDirectories(file.parent)
        Files.writeString(file, source)
        val classes = directory.resolve("bytecode")
        Files.createDirectories(classes)
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null,
            "-classpath", classes.toString(), "-d", classes.toString(), file.toString())).isZero()
    }

    private fun runner(vararg arguments: String) = GradleRunner.create()
        .withProjectDir(directory.toFile()).withPluginClasspath()
        .withArguments(*arguments, "--stacktrace").forwardOutput()
}
