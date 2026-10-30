package com.kjs.wuli3.java

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** 在 Java 构建约定上追加 Spring Boot 自动配置、配置处理器与测试支持。 */
class JavaSpringConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            pluginManager.apply("com.kjs.wuli3.java-conventions")

            dependencies {
                "implementation"("org.springframework.boot:spring-boot-autoconfigure")
                "annotationProcessor"("org.springframework.boot:spring-boot-configuration-processor")
                "testImplementation"("org.springframework.boot:spring-boot-starter-test")
            }
        }
    }
}
