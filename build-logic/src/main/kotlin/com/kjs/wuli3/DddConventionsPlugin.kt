package com.kjs.wuli3

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.Category
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.process.ExecOperations
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * 标准 DDD 模块的架构门禁；在 Java 约定之前应用，检查共享内核纯净性和基础设施依赖方向。
 *
 * @author 国杨 create on 2026/10/14 18:08
 */
class DddConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 标准生成工程使用固定模块名；本插件不代替 Java/Spring 构建约定。
        if (project.name !in DddBoundaries.allowedModules) return
        if (project.name == "shared-kernel") {
            project.extensions.extraProperties.set("wuli3.sharedKernel", true)
        }
        project.pluginManager.withPlugin("java") {
            configureChecks(project)
        }
    }

    private fun configureChecks(project: Project) {
        val main = project.extensions.getByType<SourceSetContainer>().named("main")
        // 外部服务契约必须显式标识；不根据 artifact 后缀猜测，以免误判本服务 API 和第三方库。
        val externalApiCoordinates = project.providers.gradleProperty("wuli3.ddd.external-api.coordinates")
            .map { it.split(',').map(String::trim).filter(String::isNotEmpty) }.orElse(emptyList())
        val declarations = project.tasks.register<VerifyDddDependencies>("verifyDddDependencies") {
            group = "verification"
            reportFile.convention(project.layout.buildDirectory.file("reports/ddd/verifyDddDependencies.txt"))
            description = "校验 DDD 七模块的生产依赖方向与领域纯净性。"
            violations.set(project.providers.provider {
                val externalCoordinates = externalApiCoordinates.get()
                externalCoordinates.forEach {
                    if (!Regex("[^:\\s]+:[^:\\s]+").matches(it)) {
                        throw GradleException("wuli3.ddd.external-api.coordinates 必须使用 group:artifact：$it")
                    }
                }
                listOf("compileClasspath", "runtimeClasspath", "annotationProcessor").flatMap { name ->
                    project.configurations.getByName(name).allDependencies.mapNotNull { dependency ->
                        val coordinate = "${dependency.group}:${dependency.name}"
                        val category = when (dependency) {
                            is ExternalModuleDependency -> dependency.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE)?.name
                            is ProjectDependency -> dependency.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE)?.name
                            else -> null
                        }
                        val targetModule = when (dependency) {
                            is ProjectDependency -> dependency.path.substringAfterLast(':')
                            is ExternalModuleDependency -> if (dependency.group == project.group.toString()) dependency.name else null
                            else -> null
                        }
                        when {
                            project.name != "infra" && coordinate in externalCoordinates ->
                                "外部服务 API 只能由 infra 引入：${project.name}: $name: $coordinate"
                            project.name == "domain" && DddBoundaries.isSpringGroup(dependency.group) ->
                                "domain 禁止 Spring 生产依赖：$name: $coordinate"
                            targetModule in DddBoundaries.allowedModules &&
                                targetModule !in DddBoundaries.allowedModules.getValue(project.name) ->
                                "DDD 模块依赖违规：${project.name} -> $targetModule ($name)"
                            project.name != "shared-kernel" -> null
                            category in listOf(Category.REGULAR_PLATFORM, Category.ENFORCED_PLATFORM) -> null
                            dependency is ProjectDependency && targetModule == "wuli3-core" -> null
                            coordinate in setOf("com.kjs.wuli3:wuli3-core", "org.jspecify:jspecify") -> null
                            coordinate == "org.projectlombok:lombok" && name != "runtimeClasspath" -> null
                            name == "annotationProcessor" && coordinate in setOf(
                                "com.google.errorprone:error_prone_core", "com.uber.nullaway:nullaway") -> null
                            else -> "shared-kernel 禁止生产依赖：$name: $coordinate"
                        }
                    }
                }.distinct().sorted()
            })
        }

        val toolchains = project.extensions.getByType<JavaToolchainService>()
        val java = project.extensions.getByType<org.gradle.api.plugins.JavaPluginExtension>()
        val launcher = toolchains.launcherFor(java.toolchain)
        val fingerprint = launcher.map {
            val metadata = it.metadata
            "${metadata.languageVersion}|${metadata.vendor}|${metadata.javaRuntimeVersion}|${metadata.jvmVersion}"
        }
        val types = project.tasks.register<VerifyDddTypes>("verifyDddTypes") {
            group = "verification"
            role.set(project.name)
            reportFile.convention(project.layout.buildDirectory.file("reports/ddd/verifyDddTypes.txt"))
            toolchainFingerprint.set(fingerprint)
            javap.set(launcher.map { it.metadata.installationPath.file("bin/javap") })
            basePackage.set(project.providers.gradleProperty("wuli3.ddd.base-package")
                .orElse(project.providers.gradleProperty(ConventionProperties.NULL_AWAY_ANNOTATED_PACKAGES))
                .orElse(ConventionProperties.DEFAULT_NULL_AWAY_ANNOTATED_PACKAGES))
            infraAppAllowedSuffixes.set(project.providers.gradleProperty("wuli3.ddd.types.infra-app-allowed-suffixes")
                .map { it.split(',').map(String::trim).filter(String::isNotEmpty) }
                .orElse(listOf("QueryRepository", "Query", "DTO")))
            externalApiPackages.set(project.providers.gradleProperty("wuli3.ddd.external-api.packages")
                .map { it.split(',').map(String::trim).filter(String::isNotEmpty) }
                .orElse(emptyList()))
            description = "校验 DDD 模块字节码遵守共享内核与基础设施边界。"
            dependsOn(declarations)
            classes.from(main.map { it.output.classesDirs })
            jdeps.set(toolchains.launcherFor(java.toolchain).map {
                it.metadata.installationPath.file("bin/jdeps")
            })
        }
        val transactions = project.tasks.register<VerifyDddTransactions>("verifyDddTransactions") {
            group = "verification"
            description = "可选校验 Spring 事务注解只声明在应用服务或基础设施仓储实现。"
            reportFile.convention(project.layout.buildDirectory.file("reports/ddd/verifyDddTransactions.txt"))
            toolchainFingerprint.set(fingerprint)
            enabled = project.booleanProperty("wuli3.ddd.transaction-review.enabled", false)
            basePackage.set(types.flatMap { it.basePackage })
            allowedPackages.set(project.providers.gradleProperty("wuli3.ddd.transaction-review.allowed-packages")
                .map { it.split(',').map(String::trim).filter(String::isNotEmpty) }
                .orElse(listOf("app.service", "infra.persistence")))
            allowedInfrastructureTypeSuffixes.set(project.providers.gradleProperty(
                "wuli3.ddd.transaction-review.allowed-infrastructure-type-suffixes")
                .map { it.split(',').map(String::trim).filter(String::isNotEmpty) }
                .orElse(listOf("RepositoryImpl")))
            classes.from(main.map { it.output.classesDirs })
            javap.set(toolchains.launcherFor(java.toolchain).map {
                it.metadata.installationPath.file("bin/javap")
            })
        }
        project.tasks.named("compileJava").configure { dependsOn(declarations) }
        project.tasks.named("check").configure { dependsOn(declarations, types, transactions) }
    }
}

/** 显式报告非法依赖，避免自动删除声明掩盖架构错误。 */
@CacheableTask
abstract class VerifyDddDependencies : DefaultTask() {
    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:Input
    abstract val violations: ListProperty<String>

    @TaskAction
    fun verify() {
        DddReports.clear(reportFile)
        if (violations.get().isNotEmpty()) {
            throw GradleException(violations.get().joinToString("\n"))
        }
        DddReports.success(reportFile)
    }
}

/** 使用编译工具链的 jdeps 检查字节码，包括签名、注解和方法体引用。 */
@CacheableTask
abstract class VerifyDddTypes @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:Input
    abstract val toolchainFingerprint: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val javap: RegularFileProperty

    @get:Input
    abstract val role: Property<String>

    @get:Input
    abstract val basePackage: Property<String>

    @get:Input
    abstract val infraAppAllowedSuffixes: ListProperty<String>

    /** 外部服务 API 的完整包前缀，仅允许 infra 引用这些包及其子包。 */
    @get:Input
    abstract val externalApiPackages: ListProperty<String>

    @get:Classpath
    abstract val classes: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val jdeps: RegularFileProperty

    @TaskAction
    fun verify() {
        DddReports.clear(reportFile)
        if (!Regex("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)*").matches(basePackage.get())) {
            throw GradleException("wuli3.ddd.base-package 必须是单一有效基础包，不能包含多个包：${basePackage.get()}")
        }
        externalApiPackages.get().forEach {
            if (!Regex("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)*").matches(it)) {
                throw GradleException("wuli3.ddd.external-api.packages 必须是有效完整包名：$it")
            }
        }
        val directories = classes.files.filter { it.exists() }
        val ownTypes = directories.flatMap { directory ->
            directory.walkTopDown().filter { it.extension == "class" }.map {
                it.relativeTo(directory).path.removeSuffix(".class").replace('/', '.')
            }.toList()
        }.toSet()
        ownTypes.firstOrNull { it.startsWith("${basePackage.get()}.app.acl.") }?.let {
            throw GradleException("禁止 app.acl：$it；本地能力接口请放入 domain.gateway，实现放入 infra.acl")
        }
        if (ownTypes.isEmpty()) {
            DddReports.success(reportFile)
            return
        }
        if (role.get() == "app") {
            verifyQueryInterfaces(directories, ownTypes)
        }
        val output = ByteArrayOutputStream()
        exec.exec {
            executable = jdeps.get().asFile.absolutePath
            args("-verbose:class", "-filter:none", "--ignore-missing-deps")
            args(directories.map { it.absolutePath })
            standardOutput = output
        }.assertNormalExitValue()
        val reference = Regex("^\\s+(\\S+)\\s+->\\s+(\\S+)\\s+(.+)$")
        val violations = output.toString(Charsets.UTF_8).lineSequence().mapNotNull { line ->
            val match = reference.matchEntire(line) ?: return@mapNotNull null
            val target = match.groupValues[2]
            val module = match.groupValues[3].trim()
            val root = basePackage.get()
            val targetRole = if (target in ownTypes) role.get() else if (target.startsWith("$root.")) {
                target.removePrefix("$root.").substringBefore('.').let {
                    if (it == "sharedkernel") "shared-kernel" else it
                }
            } else null
            val problem = when {
                target.startsWith("$root.app.acl.") ->
                    "禁止引用 app.acl；本地能力接口请放入 domain.gateway，实现放入 infra.acl"
                role.get() != "infra" && externalApiPackages.get().any { target.startsWith("$it.") } ->
                    "外部服务 API 类型只能由 infra 引用"
                role.get() == "domain" && target.startsWith("org.springframework.") ->
                    "domain 禁止 Spring 类型引用"
                targetRole != null && targetRole != role.get() &&
                    targetRole !in DddBoundaries.allowedModules.getValue(role.get()) ->
                    "DDD 类型依赖违规：${role.get()} -> $targetRole"
                role.get() == "infra" && targetRole == "app" &&
                    !infraAppAllowedSuffixes.get().any { target.substringAfterLast('.').endsWith(it) } ->
                    "infra 引用 app 类型必须以后缀 ${infraAppAllowedSuffixes.get().joinToString("、")} 结尾"
                role.get() == "infra" && targetRole == "api" && !target.startsWith("$root.api.dto.") ->
                    "infra 只允许引用 api.dto，不允许引用 Facade 契约"
                role.get() == "shared-kernel" && target !in ownTypes &&
                    !target.startsWith("com.kjs.wuli3.core.") && !target.startsWith("org.jspecify.annotations.") &&
                    target != "lombok.Generated" &&
                    !module.startsWith("java.") && !module.startsWith("jdk.") ->
                    "shared-kernel 禁止类型引用"
                else -> null
            }
            problem?.let { "$it：${line.trim()}" }
        }.distinct().sorted().toList()
        if (violations.isNotEmpty()) {
            throw GradleException(violations.joinToString("\n"))
        }
        DddReports.success(reportFile)
    }

    /** 查询仓储只定义接口；package-info 和接口内部的辅助类型不参与此规则。 */
    private fun verifyQueryInterfaces(directories: List<java.io.File>, ownTypes: Set<String>) {
        val repositories = ownTypes.filter {
            it.startsWith("${basePackage.get()}.app.repository.") &&
                !it.endsWith(".package-info") && '$' !in it
        }
        repositories.sorted().chunked(100).forEach { batch ->
            val output = ByteArrayOutputStream()
            exec.exec {
                executable = javap.get().asFile.absolutePath
                args("-classpath", directories.joinToString(java.io.File.pathSeparator))
                args(batch)
                standardOutput = output
            }.assertNormalExitValue()
            val interfaces = Regex("(?m)^.*\\binterface\\s+([^\\s<{]+)")
                .findAll(output.toString(Charsets.UTF_8)).map { it.groupValues[1] }.toSet()
            batch.firstOrNull { it !in interfaces }?.let {
                throw GradleException("app.repository 只允许查询仓储接口：$it")
            }
        }
    }

}

/** 声明与字节码使用同一可达依赖矩阵，避免 Gradle 的 API 传递依赖被误判。 */
internal object DddBoundaries {
    val allowedModules: Map<String, Set<String>> = mapOf(
        "shared-kernel" to emptySet(),
        "domain" to setOf("shared-kernel"),
        "api" to setOf("shared-kernel"),
        "app" to setOf("api", "domain", "shared-kernel"),
        "infra" to setOf("app", "domain", "api", "shared-kernel"),
        "adapter" to setOf("app", "api", "domain", "shared-kernel"),
        "bootstrap" to setOf("adapter", "infra", "app", "api", "domain", "shared-kernel"),
    )

    fun isSpringGroup(group: String?): Boolean =
        group == "org.springframework" || group?.startsWith("org.springframework.") == true
}
