@file:Suppress("UnstableApiUsage")

import org.gradle.api.provider.Provider
import java.util.Properties

plugins {
    `kotlin-dsl`
    `maven-publish`
}

group = "com.kjs.wuli3"
// included build 不继承根工程属性，显式读取仓库统一配置。
val repositoryProperties = providers.fileContents(layout.projectDirectory.file("../gradle.properties"))
    .asText
    .map { text -> Properties().apply { load(text.reader()) } }
version = repositoryProperties.map { it.getProperty("wuli3.version") }.get()

val kotlinVersion = providers.gradleProperty("wuli3.build.kotlin-version")
    .orElse(repositoryProperties.map { it.getProperty("wuli3.build.kotlin-version", "2.4.20") })
    .get()

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-lombok:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-allopen:$kotlinVersion")
    implementation("com.github.spotbugs.snom:spotbugs-gradle-plugin:6.5.8")
    implementation("com.diffplug.spotless:com.diffplug.spotless.gradle.plugin:8.9.0")
    implementation("de.thetaphi:forbiddenapis:3.10")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:5.1.0")
    implementation("com.tngtech.archunit:archunit:1.4.1")
    implementation("org.ow2.asm:asm:9.8")
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.1")
    testImplementation("org.assertj:assertj-core:3.27.6")
}

tasks.test {
    useJUnitPlatform()
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

gradlePlugin {
    plugins {
        register("javaConventions") {
            id = "com.kjs.wuli3.java-conventions"
            implementationClass = "com.kjs.wuli3.java.JavaConventionsPlugin"
        }
        register("dddConventions") {
            id = "com.kjs.wuli3.ddd-conventions"
            implementationClass = "com.kjs.wuli3.ddd.DddConventionsPlugin"
        }
        register("javaSpringConventions") {
            id = "com.kjs.wuli3.java-spring-conventions"
            implementationClass = "com.kjs.wuli3.java.JavaSpringConventionsPlugin"
        }
        register("kotlinConventions") {
            id = "com.kjs.wuli3.kotlin-conventions"
            implementationClass = "com.kjs.wuli3.kotlin.KotlinConventionsPlugin"
        }
        register("kotlinSpringConventions") {
            id = "com.kjs.wuli3.kotlin-spring-conventions"
            implementationClass = "com.kjs.wuli3.kotlin.KotlinSpringConventionsPlugin"
        }
        register("publishingConventions") {
            id = "com.kjs.wuli3.publishing-conventions"
            implementationClass = "com.kjs.wuli3.publishing.PublishingConventionsPlugin"
        }
    }
}

publishing {
    repositories {
        val releaseRepository = propertyOrEnvironment("centralReleasesUrl", "CENTRAL_MAVEN_RELEASES_URL")
        val snapshotRepository = propertyOrEnvironment("centralSnapshotsUrl", "CENTRAL_MAVEN_SNAPSHOTS_URL")
        val fallbackRepository = propertyOrEnvironment("centralUrl", "CENTRAL_MAVEN_URL")
        val repositoryUrl = when {
            version.toString().endsWith("-SNAPSHOT") -> snapshotRepository.orElse(fallbackRepository)
            else -> releaseRepository.orElse(fallbackRepository)
        }

        if (repositoryUrl.isPresent) {
            maven {
                name = "central"
                url = uri(repositoryUrl.get())
                // 凭据缺失时不注册 credentials 块，否则 Gradle 会因 username 未赋值而校验失败，
                // 此时按匿名发布处理。属性与凭据约定见 PublishingConventionsPlugin。
                val credentialsUsername = propertyOrEnvironment("centralUsername", "CENTRAL_MAVEN_USERNAME")
                val credentialsPassword = propertyOrEnvironment("centralPassword", "CENTRAL_MAVEN_PASSWORD")
                if (credentialsUsername.isPresent && credentialsPassword.isPresent) {
                    credentials {
                        username = credentialsUsername.get()
                        password = credentialsPassword.get()
                    }
                }
            }
        }
    }
}

/** 发布配置优先读取 Gradle 属性，再回退到环境变量；空值按未配置处理。 */
fun propertyOrEnvironment(propertyName: String, environmentVariableName: String): Provider<String> =
    providers.gradleProperty(propertyName)
        .orElse(providers.environmentVariable(environmentVariableName))
        .filter { it.isNotBlank() }
