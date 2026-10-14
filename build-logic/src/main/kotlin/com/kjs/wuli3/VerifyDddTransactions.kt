package com.kjs.wuli3

import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

/**
 * 按需检查直接声明的 Spring 事务注解只出现在应用服务边界。
 *
 * 注意：不推断组合注解、继承事务、编程式事务或运行时代理行为，也不要求所有服务声明事务。
 *
 * @author 国杨 create on 2026/10/14 18:45
 */
@DisableCachingByDefault(because = "事务位置审查只验证字节码，没有输出产物")
abstract class VerifyDddTransactions @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:Classpath
    abstract val classes: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val javap: RegularFileProperty

    @get:Input
    abstract val basePackage: Property<String>

    /** 读取实际注解段，避免把常量池中的类型引用误判为事务声明。 */
    @TaskAction
    fun verify() {
        if (!Regex("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)*").matches(basePackage.get())) {
            throw GradleException("wuli3.ddd.base-package 必须是单一有效基础包：${basePackage.get()}")
        }
        val violations = mutableListOf<String>()
        val annotationName = "org.springframework.transaction.annotation.Transactional"
        val declaration = Regex("^\\s+${Regex.escape(annotationName)}(?:\\(.*)?$")
        classes.files.filter { it.exists() }.forEach { directory ->
            directory.walkTopDown().filter { it.extension == "class" }.forEach { file ->
                val type = file.relativeTo(directory).path.removeSuffix(".class").replace('/', '.')
                val appService = type.startsWith("${basePackage.get()}.app.service.")
                val repositoryImplementation = type.startsWith("${basePackage.get()}.infra.persistence.") &&
                    type.substringAfterLast('.').endsWith("RepositoryImpl")
                if (!appService && !repositoryImplementation) {
                    val output = ByteArrayOutputStream()
                    exec.exec {
                        executable = javap.get().asFile.absolutePath
                        args("-v", "-p", file.absolutePath)
                        standardOutput = output
                    }.assertNormalExitValue()
                    var annotationIndent: Int? = null
                    val found = output.toString(Charsets.UTF_8).lineSequence().any { line ->
                        val indent = line.takeWhile(Char::isWhitespace).length
                        if (line.trim() in setOf("RuntimeVisibleAnnotations:", "RuntimeInvisibleAnnotations:")) {
                            annotationIndent = indent
                            false
                        } else {
                            val activeIndent = annotationIndent
                            if (line.isNotBlank() && activeIndent != null && indent <= activeIndent) {
                                annotationIndent = null
                            }
                            annotationIndent != null && declaration.matches(line)
                        }
                    }
                    if (found) violations.add(type)
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException("DDD 事务边界违规：Spring @Transactional 只允许声明在 app.service 或 infra.persistence.*RepositoryImpl：\n" +
                violations.sorted().joinToString("\n"))
        }
    }
}
