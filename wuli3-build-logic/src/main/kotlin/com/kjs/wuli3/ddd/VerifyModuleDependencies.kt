package com.kjs.wuli3.ddd

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/** 显式报告非法依赖，避免自动删除声明掩盖架构错误。 */
@CacheableTask
abstract class VerifyModuleDependencies : DefaultTask() {
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
