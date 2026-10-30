package com.kjs.wuli3.ddd

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/** 使用 ArchUnit 检查生产字节码的类型依赖、接口及包边界。 */
@CacheableTask
abstract class VerifyTypeBoundaries : DefaultTask() {
    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:Input
    abstract val toolchainFingerprint: Property<String>

    /** JDK 白名单读取此工具链镜像；缓存按工具链指纹区分，不绑定本机安装路径。 */
    @get:Internal
    abstract val javaHome: DirectoryProperty

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

    /** 只遍历显式导入的生产类型；没有类或没有匹配的规则对象时允许通过。 */
    @TaskAction
    fun verify() {
        DddReports.clear(reportFile)
        val root = basePackage.get()
        val moduleRole = role.get()
        val packageName = Regex("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)*")
        if (!packageName.matches(root)) {
            throw GradleException("wuli3.ddd.base-package 必须是单一有效基础包，不能包含多个包：$root")
        }
        externalApiPackages.get().forEach {
            if (!packageName.matches(it)) {
                throw GradleException("wuli3.ddd.external-api.packages 必须是有效完整包名：$it")
            }
        }
        val imported = DddClassFiles.importClasses(classes.files)
        val ownTypes = imported.map { it.name }.toSet()
        ownTypes.sorted().firstOrNull { it.startsWith("$root.app.acl.") }?.let {
            throw GradleException("禁止 app.acl：$it；外部访问接口请使用 domain.gateway.XxxGateway，实现使用 infra.gateway.XxxGatewayImpl")
        }
        if (moduleRole == "app") {
            imported.filter {
                it.name.startsWith("$root.app.repository.") &&
                    !it.name.endsWith(".package-info") && '$' !in it.name && !it.isInterface
            }.minByOrNull { it.name }?.let {
                throw GradleException("app.repository 只允许查询仓储接口：${it.name}")
            }
        }
        // 数组依赖还会指向数组类型本身，统一按元素类型应用原有边界。
        val references = imported.associate { type ->
            type.name to type.directDependenciesFromSelf
                .filterNot { it.targetClass.baseComponentType.isPrimitive }
                .associate { it.targetClass.baseComponentType.name to it.description }.toMutableMap()
        }
        DddTypeReferences.collect(classes.files).forEach { (source, targets) ->
            val descriptions = references.getValue(source)
            targets.forEach { target -> descriptions.putIfAbsent(target, "$source -> $target") }
        }
        val jdkTypes = if (moduleRole == "shared-kernel" && references.isNotEmpty()) {
            DddClassFiles.jdkTypes(javaHome.get().asFile, references.values.flatMap { it.keys }.toSet())
        } else emptySet()
        val violations = references.values.flatMap { it.entries }.mapNotNull { (target, description) ->
            val targetRole = if (target in ownTypes) moduleRole else if (target.startsWith("$root.")) {
                target.removePrefix("$root.").substringBefore('.').let {
                    if (it == "sharedkernel") "shared-kernel" else it
                }
            } else null
            val problem = when {
                target.startsWith("$root.app.acl.") ->
                    "禁止引用 app.acl；外部访问接口请使用 domain.gateway.XxxGateway，实现使用 infra.gateway.XxxGatewayImpl"
                moduleRole != "infra" && externalApiPackages.get().any { target.startsWith("$it.") } ->
                    "外部服务 API 类型只能由 infra 引用"
                moduleRole == "domain" && target.startsWith("org.springframework.") ->
                    "domain 禁止 Spring 类型引用"
                targetRole != null && targetRole != moduleRole &&
                    targetRole !in DddBoundaries.allowedModules.getValue(moduleRole) ->
                    "DDD 类型依赖违规：$moduleRole -> $targetRole"
                moduleRole == "infra" && targetRole == "app" &&
                    !infraAppAllowedSuffixes.get().any { target.substringAfterLast('.').endsWith(it) } ->
                    "infra 引用 app 类型必须以后缀 ${infraAppAllowedSuffixes.get().joinToString("、")} 结尾"
                moduleRole == "infra" && targetRole == "api" && !target.startsWith("$root.api.dto.") ->
                    "infra 只允许引用 api.dto，不允许引用 Facade 契约"
                moduleRole == "shared-kernel" && target !in ownTypes &&
                    !target.startsWith("com.kjs.wuli3.core.") && !target.startsWith("org.jspecify.annotations.") &&
                    target != "lombok.Generated" && target !in jdkTypes ->
                    "shared-kernel 禁止类型引用"
                else -> null
            }
            problem?.let { "$it：$description" }
        }.distinct().sorted()
        if (violations.isNotEmpty()) {
            throw GradleException(violations.joinToString("\n"))
        }
        DddReports.success(reportFile)
    }
}
