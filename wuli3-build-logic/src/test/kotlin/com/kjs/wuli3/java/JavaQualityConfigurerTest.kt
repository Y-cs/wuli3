package com.kjs.wuli3.java

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class JavaQualityConfigurerTest {
    @TempDir
    lateinit var projectDir: Path

    @Test
    fun `quality compiler dependencies use the same BOM as annotation processors`() {
        qualityFixture()
        write("build.gradle.kts", """
            plugins { id("com.kjs.wuli3.java-conventions") }
            tasks.register("verifyCompilerDependencies") {
                doLast {
                    val checkers = configurations.getByName("errorprone").dependencies
                    check(checkers.toList().all { it.version == null })
                    listOf("annotationProcessor", "testAnnotationProcessor").forEach { name ->
                        val selected = configurations.getByName(name).resolvedConfiguration.resolvedArtifacts
                            .map { it.moduleVersion.id.toString() }.toSet()
                        check("com.google.errorprone:error_prone_core:2.50.0" in selected)
                        check("com.uber.nullaway:nullaway:0.13.7" in selected)
                    }
                }
            }
        """.trimIndent())
        assertThat(runner("verifyCompilerDependencies").build().output).contains("BUILD SUCCESSFUL")
    }

    @Test
    fun `formatter output with an unbreakable long literal passes style checks`() {
        qualityFixture()
        write("src/main/java/example/LongLiteral.java", """
            package example;
            public final class LongLiteral {
                private LongLiteral() {}
                public static final String VALUE = "${"x".repeat(180)}";
            }
        """.trimIndent())
        runner("spotlessApply").build()
        assertThat(runner("spotlessCheck", "checkstyleMain").build().output).contains("BUILD SUCCESSFUL")
    }

    @Test
    fun `lombok constructors getters data and builders compile with NullAway`() {
        qualityFixture(lombok = true)
        write("src/main/java/example/Customer.java", """
            package example;
            @lombok.Data
            @lombok.Builder
            public class Customer {
                @lombok.NonNull private final String id;
                private final @org.jspecify.annotations.Nullable String nickname;
                @lombok.Singular private final java.util.List<String> tags;
            }
        """.trimIndent())
        write("src/main/java/example/CustomerService.java", """
            package example;
            @lombok.RequiredArgsConstructor
            public final class CustomerService {
                private final Customer customer;
                public String id() { return this.customer.getId(); }
                public @org.jspecify.annotations.Nullable String nickname() { return this.customer.getNickname(); }
                public static Customer create() { return Customer.builder().id("42").build(); }
            }
        """.trimIndent())
        write("src/main/java/example/Address.java", """
            package example;
            @lombok.Value
            @lombok.With
            public class Address {
                @lombok.NonNull String city;
            }
        """.trimIndent())
        write("src/test/java/example/CustomerFixtures.java", """
            package example;
            @lombok.RequiredArgsConstructor
            public final class CustomerFixtures {
                @lombok.Getter private final Customer customer;
                public static CustomerFixtures create() {
                    return new CustomerFixtures(Customer.builder().id("42").tag("test").build());
                }
                public static Address move() { return new Address("old").withCity("new"); }
            }
        """.trimIndent())
        assertThat(runner("compileJava", "compileTestJava").build().output).contains("BUILD SUCCESSFUL")
    }

    @Test
    fun `NullAway still rejects handwritten null returns in a Lombok class`() {
        qualityFixture(lombok = true)
        write("src/main/java/example/BrokenService.java", """
            package example;
            @lombok.RequiredArgsConstructor
            public final class BrokenService {
                private final String name;
                @lombok.Getter private final @org.jspecify.annotations.Nullable String nickname;
                public String broken() { return null; }
                public String nickname() { return this.getNickname(); }
            }
        """.trimIndent())
        assertThat(runner("compileJava").buildAndFail().output)
            .contains("[NullAway]", "BrokenService.java", "return null", "return this.getNickname()")
    }

    private fun qualityFixture(lombok: Boolean = false) {
        write("settings.gradle.kts", """
            dependencyResolutionManagement { repositories { mavenCentral() } }
            rootProject.name = "quality-fixture"
            include(":bom")
        """.trimIndent())
        write("gradle.properties", """
            wuli3.conventions.use-project-bom=true
            wuli3.conventions.project-bom-path=:bom
            wuli3.conventions.nullaway.annotated-packages=example
            wuli3.conventions.lombok.enabled=$lombok
        """.trimIndent())
        write("build.gradle.kts", "plugins { id(\"com.kjs.wuli3.java-conventions\") }")
        write("bom/build.gradle.kts", """
            plugins { `java-platform` }
            dependencies {
                constraints {
                    api("org.slf4j:slf4j-api:2.0.17")
                    api("org.jspecify:jspecify:1.0.0")
                    api("org.projectlombok:lombok:1.18.46")
                    api("com.google.errorprone:error_prone_core:2.50.0")
                    api("com.uber.nullaway:nullaway:0.13.7")
                    api("org.junit.jupiter:junit-jupiter:6.0.1")
                    api("org.junit.platform:junit-platform-launcher:6.0.1")
                    api("org.assertj:assertj-core:3.27.6")
                }
            }
        """.trimIndent())
    }

    private fun runner(vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments(*arguments, "--stacktrace")

    @Test
    fun `dubbo reference fields are excluded from NullAway initialization checks`() {
        write("settings.gradle.kts", """
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
            dependencyResolutionManagement { repositories { mavenCentral() } }
            rootProject.name = "nullaway-dubbo-reference"
            include(":bom")
        """.trimIndent())
        write("gradle.properties", """
            wuli3.conventions.nullaway.annotated-packages=example
            wuli3.conventions.use-project-bom=true
            wuli3.conventions.project-bom-path=:bom
            wuli3.conventions.lombok.enabled=false
        """.trimIndent())
        write("bom/build.gradle.kts", """
            plugins { `java-platform` }
            dependencies {
                constraints {
                    api("org.slf4j:slf4j-api:2.0.17")
                    api("com.google.errorprone:error_prone_core:2.50.0")
                    api("com.uber.nullaway:nullaway:0.13.7")
                }
            }
        """.trimIndent())
        write("build.gradle.kts", """
            plugins {
                id("com.kjs.wuli3.java-conventions")
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
