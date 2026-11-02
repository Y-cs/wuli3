package com.kjs.wuli3.common

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** 验证约定属性的默认值、覆盖值及非法输入处理，避免整理配置定义时改变消费方行为。 */
class ConventionPropertiesTest {
    @TempDir
    lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `Java conventions use defaults or explicit Gradle properties`(overridden: Boolean) {
        writeFixture()
        if (overridden) {
            Files.writeString(directory.resolve("gradle.properties"), """
                wuli3.conventions.java-version=17
                wuli3.conventions.bom-coordinates=example:custom-bom:2.0
                wuli3.conventions.lombok.enabled=false
                wuli3.conventions.jacoco.line.minimum=0.75
                wuli3.conventions.jacoco.verification.enabled=false
                wuli3.conventions.forbidden-apis.enabled=false
                wuli3.conventions.forbidden-apis.test-enabled=true
                wuli3.conventions.spotless.enabled=false
                wuli3.conventions.spotbugs.enabled=false
            """.trimIndent())
        }
        Files.writeString(directory.resolve("build.gradle"), """
            plugins { id 'com.kjs.wuli3.java-conventions' }

            tasks.register('verifyProperties') {
                doLast {
                    assert java.toolchain.languageVersion.get().asInt() == ${if (overridden) 17 else 21}
                    def bom = configurations.implementation.dependencies.find { it.name == '${if (overridden) "custom-bom" else "wuli3-dependencies"}' }
                    assert bom.group == '${if (overridden) "example" else "com.kjs.wuli3"}'
                    assert bom.version == '${if (overridden) "2.0" else "1.0.0"}'
                    assert configurations.compileOnly.dependencies.any { it.name == 'lombok' } == ${!overridden}
                    def coverage = tasks.jacocoTestCoverageVerification
                    assert coverage.enabled == ${!overridden}
                    assert coverage.violationRules.rules[0].limits[0].minimum == new BigDecimal('${if (overridden) "0.75" else "0.45"}')
                    assert tasks.forbiddenApisMain.enabled == ${!overridden}
                    assert !tasks.forbiddenApisTest.enabled
                    assert tasks.spotlessJavaCheck.enabled == ${!overridden}
                    assert tasks.spotbugsMain.enabled == ${!overridden}
                }
            }
        """.trimIndent())

        assertThat(runner("verifyProperties").build().output).contains("BUILD SUCCESSFUL")
    }

    @ParameterizedTest
    @ValueSource(strings = ["wuli3.conventions.lombok.enabled=TRUE", "wuli3.conventions.java-version=invalid"])
    fun `invalid boolean and integer properties fail configuration`(property: String) {
        writeFixture()
        Files.writeString(directory.resolve("build.gradle"), "plugins { id 'com.kjs.wuli3.java-conventions' }")

        assertThat(runner("help", "-P$property").buildAndFail().output).contains(property.substringAfter('='))
    }

    private fun writeFixture() {
        Files.writeString(directory.resolve("settings.gradle"), "rootProject.name = 'convention-properties'")
        // Spotless 需要存在源码才会创建对应语言的检查任务。
        val source = directory.resolve("src/main/java/example/Example.java")
        Files.createDirectories(source.parent)
        Files.writeString(source, "package example; public class Example {}")
    }

    private fun runner(vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(directory.toFile())
        .withPluginClasspath()
        .withArguments(*arguments, "--stacktrace")
}
