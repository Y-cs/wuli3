package com.kjs.wuli3

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class KotlinConventionsPluginTest {
    @TempDir
    lateinit var projectDir: Path

    @Test
    fun `mixed Java and Kotlin sources compile in both directions and lombok is available`() {
        writeFixture()
        write("app/src/main/java/example/JavaMain.java", """
            package example;
            public final class JavaMain {
                public static String javaValue() { return KotlinMain.kotlinValue(); }
            }
        """.trimIndent())
        write("app/src/main/kotlin/example/KotlinMain.kt", """
            package example
            object KotlinMain { @JvmStatic fun kotlinValue(): String = JavaPojo().name }
        """.trimIndent())
        write("app/src/main/java/example/JavaPojo.java", """
            package example;
            import lombok.Getter;
            public class JavaPojo { @Getter private final String name = "ok"; }
        """.trimIndent())
        write("app/src/test/java/example/JavaTestHelper.java", """
            package example;
            public final class JavaTestHelper { public static String value() { return KotlinTest.kotlinTestValue(); } }
        """.trimIndent())
        write("app/src/test/kotlin/example/KotlinTest.kt", """
            package example
            fun kotlinTestValue(): String = JavaMain.javaValue() + JavaTestHelper.valueFromJava()
            object KotlinTest { @JvmStatic fun kotlinTestValue(): String = JavaMain.javaValue() }
        """.trimIndent())
        write("app/src/test/java/example/JavaTestHelper.java", """
            package example;
            public final class JavaTestHelper {
                public static String value() { return KotlinTest.kotlinTestValue(); }
                public static String valueFromJava() { return "-java"; }
            }
        """.trimIndent())

        val result = run(":app:compileTestJava")

        assertThat(result.task(":app:compileKotlin")!!.outcome)
            .isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.NO_SOURCE)
        assertThat(result.task(":app:compileJava")!!.outcome)
            .isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.NO_SOURCE)
        assertThat(result.task(":app:compileTestKotlin")!!.outcome)
            .isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.NO_SOURCE)
        assertThat(result.task(":app:compileTestJava")!!.outcome)
            .isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.NO_SOURCE)
        assertThat(result.output.indexOf(":compileKotlin")).isLessThan(result.output.indexOf(":compileJava"))
        assertThat(result.output.indexOf(":compileTestKotlin")).isLessThan(result.output.indexOf(":compileTestJava"))
    }

    @Test
    fun `kotlin sources are formatted by spotless`() {
        writeFixture()
        write("app/src/main/kotlin/example/Bad.kt", "package example\n\nfun  bad( ):String=\"bad\"")

        runExpectFailure(":app:spotlessKotlinCheck")
        run(":app:spotlessKotlinApply")
        run(":app:spotlessKotlinCheck")
        assertThat(Files.readString(projectDir.resolve("app/src/main/kotlin/example/Bad.kt")))
            .contains("fun bad(): String")
    }

    @Test
    fun `compile tasks reuse configuration cache`() {
        writeFixture()
        write("app/src/main/kotlin/example/OnlyKotlin.kt", "package example\nclass OnlyKotlin")

        run(":app:compileJava", "--configuration-cache")
        val second = run(":app:compileJava", "--configuration-cache")

        assertThat(second.output).contains("Reusing configuration cache")
    }

    private fun writeFixture() {
        write("settings.gradle.kts", """
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
            dependencyResolutionManagement { repositories { mavenCentral() } }
            rootProject.name = "fixture"
            include(":wuli3-dependencies", ":app")
        """.trimIndent())
        write("gradle.properties", """
            wuli3.conventions.use-project-bom=true
            wuli3.conventions.project-bom-path=:wuli3-dependencies
            wuli3.conventions.spotbugs-enabled=false
            wuli3.conventions.forbidden-apis-enabled=false
            wuli3.conventions.null-away-enabled=false
            wuli3.conventions.jacoco-verification-enabled=false
        """.trimIndent())
        write("build.gradle.kts", "")
        write("wuli3-dependencies/build.gradle.kts", """
            plugins { `java-platform` }
            javaPlatform { allowDependencies() }
            dependencies {
                constraints {
                    api("org.slf4j:slf4j-api:2.0.17")
                    api("org.jspecify:jspecify:1.0.0")
                    api("org.projectlombok:lombok:1.18.42")
                    api("org.junit.jupiter:junit-jupiter:6.0.1")
                    api("org.junit.platform:junit-platform-launcher:6.0.1")
                    api("org.assertj:assertj-core:3.27.6")
                }
            }
        """.trimIndent())
        write("app/build.gradle.kts", """
            plugins { id("com.kjs.wuli3.kotlin-conventions") }
        """.trimIndent())
    }

    private fun write(relativePath: String, content: String) {
        val file = projectDir.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }

    private fun run(vararg arguments: String) = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments(*arguments, "--stacktrace")
        .forwardOutput()
        .build()

    private fun runExpectFailure(vararg arguments: String) = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments(*arguments, "--stacktrace")
        .forwardOutput()
        .buildAndFail()

}
