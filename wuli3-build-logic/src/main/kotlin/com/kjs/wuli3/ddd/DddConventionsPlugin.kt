package com.kjs.wuli3.ddd

import com.kjs.wuli3.common.ConventionProperties
import com.kjs.wuli3.common.booleanProperty
import com.kjs.wuli3.common.stringPropertyProvider
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.Category
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

/**
 * 标准 DDD 模块的架构门禁；在 Java 约定之前应用，检查共享内核纯净性和基础设施依赖方向。
 *
 * @author 国杨 create on 2026/10/14 18:08
 */
class DddConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 模块名可以是标准角色名，也可以是带服务名前缀的 <service>-<角色>。本插件不代替 Java/Spring 构建约定。
        val moduleRole = DddBoundaries.roleOf(project.name) ?: return
        if (moduleRole == "shared-kernel") {
            project.extensions.extraProperties.set("wuli3.sharedKernel", true)
        }
        project.pluginManager.withPlugin("java") {
            configureChecks(project, moduleRole)
        }
    }

    private fun configureChecks(project: Project, moduleRole: String) {
        val main = project.extensions.getByType<SourceSetContainer>().named("main")
        // 外部服务契约必须显式标识；不根据 artifact 后缀猜测，以免误判本服务 API 和第三方库。
        val externalApiCoordinates = project.providers.gradleProperty("wuli3.ddd.external-api.coordinates")
            .map { it.split(',').map(String::trim).filter(String::isNotEmpty) }.orElse(emptyList())
        val declarations = project.tasks.register<VerifyModuleDependencies>("verifyModuleDependencies") {
            group = "verification"
            reportFile.convention(project.layout.buildDirectory.file("reports/ddd/verifyModuleDependencies.txt"))
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
                        val targetRole = targetModule?.let(DddBoundaries::roleOf)
                        when {
                            moduleRole != "infra" && coordinate in externalCoordinates ->
                                "外部服务 API 只能由 infra 引入：${project.name}: $name: $coordinate"
                            moduleRole == "domain" && DddBoundaries.isSpringGroup(dependency.group) ->
                                "domain 禁止 Spring 生产依赖：$name: $coordinate"
                            targetRole != null && targetRole !in DddBoundaries.allowedModules.getValue(moduleRole) ->
                                "DDD 模块依赖违规：${project.name} -> $targetModule ($name)"
                            moduleRole != "shared-kernel" -> null
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
        val types = project.tasks.register<VerifyTypeBoundaries>("verifyTypeBoundaries") {
            group = "verification"
            role.set(moduleRole)
            reportFile.convention(project.layout.buildDirectory.file("reports/ddd/verifyTypeBoundaries.txt"))
            toolchainFingerprint.set(fingerprint)
            javaHome.set(launcher.map { it.metadata.installationPath })
            basePackage.set(project.providers.gradleProperty("wuli3.ddd.base-package")
                .orElse(project.stringPropertyProvider(ConventionProperties.NULL_AWAY_ANNOTATED_PACKAGES)))
            infraAppAllowedSuffixes.set(project.providers.gradleProperty("wuli3.ddd.types.infra-app-allowed-suffixes")
                .map { it.split(',').map(String::trim).filter(String::isNotEmpty) }
                .orElse(listOf("QueryRepository", "Query", "DTO")))
            externalApiPackages.set(project.providers.gradleProperty("wuli3.ddd.external-api.packages")
                .map { it.split(',').map(String::trim).filter(String::isNotEmpty) }
                .orElse(emptyList()))
            description = "校验 DDD 模块字节码遵守共享内核与基础设施边界。"
            dependsOn(declarations)
            classes.from(main.map { it.output.classesDirs })
        }
        val transactions = project.tasks.register<VerifyTransactionBoundaries>("verifyTransactionBoundaries") {
            group = "verification"
            description = "可选校验 Spring 事务注解只声明在应用服务或基础设施仓储实现。"
            reportFile.convention(project.layout.buildDirectory.file("reports/ddd/verifyTransactionBoundaries.txt"))
            enabled = project.booleanProperty(ConventionProperties.TRANSACTION_REVIEW_ENABLED)
            basePackage.set(types.flatMap { it.basePackage })
            allowedPackages.set(project.providers.gradleProperty("wuli3.ddd.transaction-review.allowed-packages")
                .map { it.split(',').map(String::trim).filter(String::isNotEmpty) }
                .orElse(listOf("app.service", "infra.persistence")))
            allowedInfrastructureTypeSuffixes.set(project.providers.gradleProperty(
                "wuli3.ddd.transaction-review.allowed-infrastructure-type-suffixes")
                .map { it.split(',').map(String::trim).filter(String::isNotEmpty) }
                .orElse(listOf("RepositoryImpl")))
            classes.from(main.map { it.output.classesDirs })
        }
        project.tasks.named("compileJava").configure { dependsOn(declarations) }
        project.tasks.named("check").configure { dependsOn(declarations, types, transactions) }
    }
}
