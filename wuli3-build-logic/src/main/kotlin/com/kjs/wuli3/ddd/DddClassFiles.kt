package com.kjs.wuli3.ddd

import com.tngtech.archunit.ArchConfiguration
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.function.Function
import org.gradle.api.GradleException

/** 只导入任务声明的类文件，隔离 Gradle daemon 和其他模块的运行时类路径。 */
internal object DddClassFiles {
    /** 缺失依赖仅保留名称等字节码信息；不自动扫描外部类型、测试输出或消费工程依赖。 */
    fun importClasses(directories: Collection<File>): JavaClasses = ArchConfiguration.withThreadLocalScope(
        Function<ArchConfiguration, JavaClasses> { configuration ->
            configuration.setResolveMissingDependenciesFromClassPath(false)
            configuration.unsetClassResolver()
            // JVM 的 archunit.* 系统属性会覆盖局部配置；发现冲突时明确失败，避免隐式增加缓存输入。
            if (configuration.resolveMissingDependenciesFromClassPath() || configuration.classResolver.isPresent) {
                throw GradleException("DDD 检查禁止 ArchUnit 自动解析外部类，请移除冲突的 archunit.* 系统属性")
            }
            val existing = directories.filter(File::exists)
            val expected = existing.flatMap { directory ->
                directory.walkTopDown().filter { it.extension == "class" && it.name != "module-info.class" }
                    .map { it.relativeTo(directory).path.removeSuffix(".class").replace(File.separatorChar, '.') }
                    .toList()
            }.toSet()
            val imported = ClassFileImporter().importPaths(existing.map(File::toPath))
            // ArchUnit 对损坏或不支持的类文件可能只记录告警，不能将遗漏的类当作检查通过。
            val missing = expected - imported.map { it.name }.toSet()
            if (missing.isNotEmpty()) {
                throw GradleException("无法完整导入生产类：${missing.sorted().joinToString()}，请检查类文件格式和 ArchUnit 版本")
            }
            imported
        },
    )

    /** 按编译工具链的真实运行时镜像识别 JDK 类型，兼容 javax、org.w3c 等非 java 包名。 */
    fun jdkTypes(javaHome: File, candidates: Set<String>): Set<String> =
        FileSystems.newFileSystem(URI.create("jrt:/"), mapOf("java.home" to javaHome.absolutePath)).use { image ->
            val modules = Files.list(image.getPath("/modules")).use { paths ->
                paths.filter {
                    val name = it.fileName.toString()
                    name.startsWith("java.") || name.startsWith("jdk.")
                }.toList()
            }
            candidates.filterTo(mutableSetOf()) { name ->
                val path = name.replace('.', '/') + ".class"
                modules.any { Files.isRegularFile(it.resolve(path)) }
            }
        }
}
