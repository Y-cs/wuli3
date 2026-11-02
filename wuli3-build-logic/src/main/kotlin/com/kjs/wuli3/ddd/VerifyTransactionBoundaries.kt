package com.kjs.wuli3.ddd

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * 按需检查直接声明的 Spring 事务注解只出现在应用服务边界。
 *
 * 注意：不推断组合注解、继承事务、编程式事务或运行时代理行为，也不要求所有服务声明事务。
 *
 * @author 国杨 create on 2026/10/14 18:45
 */
@CacheableTask
abstract class VerifyTransactionBoundaries : DefaultTask() {
    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:Classpath
    abstract val classes: ConfigurableFileCollection

    @get:Input
    abstract val basePackage: Property<String>

    /** 允许声明事务的相对包；infra.persistence 包仍受仓储实现后缀约束。 */
    @get:Input
    abstract val allowedPackages: ListProperty<String>

    /** 基础设施包中允许声明事务的类型后缀。 */
    @get:Input
    abstract val allowedInfrastructureTypeSuffixes: ListProperty<String>

    /** 通过注解模型检查本类直接声明，避免把常量池引用、继承成员或组合注解误判为声明。 */
    @TaskAction
    fun verify() {
        DddReports.clear(reportFile)
        val root = basePackage.get()
        if (!Regex("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)*").matches(root)) {
            throw GradleException("wuli3.ddd.base-package 必须是单一有效基础包：$root")
        }
        val annotationName = "org.springframework.transaction.annotation.Transactional"
        val packages = allowedPackages.get().map { it.trim().trim('.') }.filter { it.isNotEmpty() }.toSet()
        val infrastructureSuffixes = allowedInfrastructureTypeSuffixes.get().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        fun allowed(type: String): Boolean {
            if (!type.startsWith("$root.")) return false
            val relative = type.removePrefix("$root.")
            val packageName = relative.substringBeforeLast('.', "")
            val matched = packages.any { packageName == it || packageName.startsWith("$it.") }
            if (!matched) return false
            return if (packageName == "infra.persistence" || packageName.startsWith("infra.persistence.")) {
                infrastructureSuffixes.any { relative.substringAfterLast('.').endsWith(it) }
            } else true
        }
        val violations = DddClassFiles.importClasses(classes.files).filter { type ->
            !allowed(type.name) && (
                type.isAnnotatedWith(annotationName) ||
                    type.codeUnits.any { it.isAnnotatedWith(annotationName) } ||
                    type.fields.any { it.isAnnotatedWith(annotationName) }
                )
        }.map { it.name }.sorted()
        if (violations.isNotEmpty()) {
            throw GradleException("DDD 事务边界违规：Spring @Transactional 只允许声明在 app.service 或 infra.persistence.*RepositoryImpl：\n" +
                violations.joinToString("\n"))
        }
        DddReports.success(reportFile)
    }
}
