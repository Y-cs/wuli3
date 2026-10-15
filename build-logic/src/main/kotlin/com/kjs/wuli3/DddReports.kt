package com.kjs.wuli3

import org.gradle.api.file.RegularFileProperty
import java.nio.file.Files

/**
 * 管理架构门禁的确定性成功报告，失败时不保留上次成功标记。
 *
 * @author 国杨 create on 2026/10/14 20:00
 */
internal object DddReports {
    fun clear(report: RegularFileProperty) {
        Files.deleteIfExists(report.get().asFile.toPath())
    }

    fun success(report: RegularFileProperty) {
        val file = report.get().asFile.toPath()
        Files.createDirectories(file.parent)
        Files.writeString(file, "DDD verification passed.\n")
    }
}
