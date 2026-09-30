package com.kjs.wuli3

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** Kotlin/JVM 与 Java 混编模块的统一构建约定。 */
class KotlinConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("com.kjs.wuli3.java-conventions")

            if (booleanProperty(ConventionProperties.LOMBOK_ENABLED, true)) {
                pluginManager.apply("org.jetbrains.kotlin.plugin.lombok")
            }

            val javaVersion = intProperty(
                ConventionProperties.JAVA_VERSION,
                ConventionProperties.DEFAULT_JAVA_VERSION,
            )
            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
                    javaParameters.set(true)
                }
            }

            // Kotlin 先产出字节码和 Java 可见的符号，Java 再编译同一 source set。
            tasks.named("compileJava") {
                dependsOn("compileKotlin")
            }
            tasks.named("compileTestJava") {
                dependsOn("compileTestKotlin")
            }

            dependencies {
                add("testImplementation", "org.jetbrains.kotlin:kotlin-test")
            }
        }
    }
}

/** 为需要 Spring 注解开放能力的 Kotlin 模块追加 Spring Kotlin 约定。 */
class KotlinSpringConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            pluginManager.apply("com.kjs.wuli3.kotlin-conventions")
            pluginManager.apply("org.jetbrains.kotlin.plugin.spring")
            dependencies {
                add("implementation", "org.jetbrains.kotlin:kotlin-reflect")
            }
        }
    }
}
