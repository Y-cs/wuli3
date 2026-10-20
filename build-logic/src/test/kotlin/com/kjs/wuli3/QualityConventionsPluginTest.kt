package com.kjs.wuli3

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class QualityConventionsPluginTest {
    @TempDir
    lateinit var projectDir: Path

    @Test
    fun `dubbo reference fields are excluded from NullAway initialization checks`() {
        write("settings.gradle.kts", """
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
            dependencyResolutionManagement { repositories { mavenCentral() } }
            rootProject.name = "nullaway-dubbo-reference"
        """.trimIndent())
        write("gradle.properties", "wuli3.conventions.nullaway.annotated-packages=example")
        write("build.gradle.kts", """
            plugins {
                java
                id("com.kjs.wuli3.quality-conventions")
            }

            dependencies {
                compileOnly("org.jspecify:jspecify:1.0.0")
            }
        """.trimIndent())
        write("src/main/java/org/apache/dubbo/config/annotation/DubboReference.java", """
            package org.apache.dubbo.config.annotation;

            @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
            @java.lang.annotation.Target(java.lang.annotation.ElementType.FIELD)
            public @interface DubboReference {}
        """.trimIndent())
        write("src/main/java/example/DubboClient.java", """
            package example;

            public final class DubboClient {}
        """.trimIndent())
        write("src/main/java/example/DubboClientHolder.java", """
            package example;

            import org.apache.dubbo.config.annotation.DubboReference;

            public final class DubboClientHolder {
                @DubboReference
                private DubboClient client;

                public DubboClient client() {
                    return this.client;
                }
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("compileJava", "--stacktrace")
            .build()

        assertThat(result.output).contains("BUILD SUCCESSFUL")
    }

    private fun write(relativePath: String, content: String) {
        val file = projectDir.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }
}
