package com.kjs.wuli3.java

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * 验证 Java Spring 插件入口及其继承的 Java 质量约定。
 *
 * @author 国杨 create on 2026/10/30 11:13
 */
class JavaSpringConventionsPluginTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `java spring entry configures Java quality and Spring dependencies`() {
        Files.writeString(directory.resolve("settings.gradle.kts"), "rootProject.name = \"fixture\"")
        Files.writeString(directory.resolve("build.gradle.kts"), """
            plugins { id("com.kjs.wuli3.java-spring-conventions") }

            tasks.register("verifyConventions") {
                doLast {
                    check(pluginManager.hasPlugin("com.kjs.wuli3.java-conventions"))
                    check(pluginManager.hasPlugin("java-library"))
                    check(pluginManager.hasPlugin("checkstyle"))
                    check(pluginManager.hasPlugin("net.ltgt.errorprone"))
                    check(tasks.findByName("spotlessJavaCheck") != null)
                    val expected = mapOf(
                        "implementation" to "spring-boot-autoconfigure",
                        "annotationProcessor" to "spring-boot-configuration-processor",
                        "testImplementation" to "spring-boot-starter-test",
                    )
                    expected.forEach { (configuration, dependency) ->
                        check(configurations.getByName(configuration).dependencies.any {
                            it.group == "org.springframework.boot" && it.name == dependency
                        })
                    }
                }
            }
        """.trimIndent())
        val result = GradleRunner.create()
            .withProjectDir(directory.toFile())
            .withPluginClasspath()
            .withArguments("verifyConventions", "--stacktrace")
            .build()
        assertThat(result.output).contains("BUILD SUCCESSFUL")
    }
}
