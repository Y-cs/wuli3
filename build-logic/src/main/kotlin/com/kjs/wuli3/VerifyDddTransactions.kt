package com.kjs.wuli3

import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputFile

/**
 * 按需检查直接声明的 Spring 事务注解只出现在应用服务边界。
 *
 * 注意：不推断组合注解、继承事务、编程式事务或运行时代理行为，也不要求所有服务声明事务。
 *
 * @author 国杨 create on 2026/10/14 18:45
 */
@CacheableTask
abstract class VerifyDddTransactions @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:Input
    abstract val toolchainFingerprint: Property<String>

    @get:Classpath
    abstract val classes: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val javap: RegularFileProperty

    @get:Input
    abstract val basePackage: Property<String>

    /** 允许声明事务的相对包；infra.persistence 包仍受仓储实现后缀约束。 */
    @get:Input
    abstract val allowedPackages: ListProperty<String>

    /** 基础设施包中允许声明事务的类型后缀。 */
    @get:Input
    abstract val allowedInfrastructureTypeSuffixes: ListProperty<String>

    /** 读取实际注解段，避免把常量池中的类型引用误判为事务声明。 */
    @TaskAction
    fun verify() {
        DddReports.clear(reportFile)
        if (!Regex("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)*").matches(basePackage.get())) {
            throw GradleException("wuli3.ddd.base-package 必须是单一有效基础包：${basePackage.get()}")
        }
        val violations = mutableListOf<String>()
        val annotationName = "org.springframework.transaction.annotation.Transactional"
        val declaration = Regex("^\\s+${Regex.escape(annotationName)}(?:\\(.*)?$")
        val root = basePackage.get()
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
        val candidates = classes.files.filter { it.exists() }.flatMap { directory ->
            directory.walkTopDown().filter { it.extension == "class" }.map { file ->
                file to file.relativeTo(directory).path.removeSuffix(".class").replace('/', '.')
            }.filter { (_, type) ->
                !allowed(type)
            }.toList()
        }.sortedBy { it.second }
        candidates.chunked(100).forEach { batch ->
            val output = ByteArrayOutputStream()
            exec.exec {
                executable = javap.get().asFile.absolutePath
                args("-v", "-p")
                args(batch.map { it.first.absolutePath })
                standardOutput = output
            }.assertNormalExitValue()
            var type: String? = null
            var annotationIndent: Int? = null
            val visited = mutableSetOf<String>()
            output.toString(Charsets.UTF_8).lineSequence().forEach { line ->
                if (line.startsWith("Classfile ")) {
                    type = null
                    annotationIndent = null
                }
                if (line.trimStart().startsWith("this_class:")) {
                    type = line.substringAfter("//", "").trim().replace('/', '.')
                    visited.add(type!!)
                }
                val indent = line.takeWhile(Char::isWhitespace).length
                if (line.trim() in setOf("RuntimeVisibleAnnotations:", "RuntimeInvisibleAnnotations:")) {
                    annotationIndent = indent
                } else {
                    val activeIndent = annotationIndent
                    if (line.isNotBlank() && activeIndent != null && indent <= activeIndent) {
                        annotationIndent = null
                    }
                    if (annotationIndent != null && declaration.matches(line)) {
                        violations.add(type ?: throw GradleException("无法识别 javap 注解所属类型"))
                    }
                }
            }
            val missing = batch.map { it.second }.filter { it !in visited }
            if (missing.isNotEmpty()) throw GradleException("无法识别 javap 类型输出：${missing.joinToString()}")
        }
        if (violations.isNotEmpty()) {
            throw GradleException("DDD 事务边界违规：Spring @Transactional 只允许声明在 app.service 或 infra.persistence.*RepositoryImpl：\n" +
                violations.distinct().sorted().joinToString("\n"))
        }
        DddReports.success(reportFile)
    }
}
