import org.gradle.api.publish.maven.MavenPublication
import java.util.Properties

plugins {
    `kotlin-dsl`
    `maven-publish`
}

group = "com.kjs.wuli3"
// build-logic 是独立构建，读取 gradle.properties
version = providers.fileContents(layout.projectDirectory.file("../gradle.properties"))
    .asText
    .map { text -> Properties().apply { load(text.reader()) }.getProperty("wuli3.version") }
    .get()

val kotlinVersion = "2.4.20"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-lombok:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-allopen:$kotlinVersion")
    implementation("com.github.spotbugs.snom:spotbugs-gradle-plugin:6.5.8")
    implementation("com.diffplug.spotless:com.diffplug.spotless.gradle.plugin:8.9.0")
    implementation("de.thetaphi:forbiddenapis:3.10")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:5.1.0")
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
            implementationClass = "com.kjs.wuli3.JavaConventionsPlugin"
        }
        register("qualityConventions") {
            id = "com.kjs.wuli3.quality-conventions"
            implementationClass = "com.kjs.wuli3.QualityConventionsPlugin"
        }
        register("springConventions") {
            id = "com.kjs.wuli3.spring-conventions"
            implementationClass = "com.kjs.wuli3.SpringConventionsPlugin"
        }
        register("kotlinConventions") {
            id = "com.kjs.wuli3.kotlin-conventions"
            implementationClass = "com.kjs.wuli3.KotlinConventionsPlugin"
        }
        register("kotlinSpringConventions") {
            id = "com.kjs.wuli3.kotlin-spring-conventions"
            implementationClass = "com.kjs.wuli3.KotlinSpringConventionsPlugin"
        }
        register("publishingConventions") {
            id = "com.kjs.wuli3.publishing-conventions"
            implementationClass = "com.kjs.wuli3.PublishingConventionsPlugin"
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        if (name == "pluginMaven") {
            artifactId = "build-logic"
        }
    }

    repositories {
        val releaseRepository = providers.gradleProperty("centralReleasesUrl")
            .orElse(providers.environmentVariable("CENTRAL_MAVEN_RELEASES_URL"))
            .filter { it.isNotBlank() }
        val snapshotRepository = providers.gradleProperty("centralSnapshotsUrl")
            .orElse(providers.environmentVariable("CENTRAL_MAVEN_SNAPSHOTS_URL"))
            .filter { it.isNotBlank() }
        val fallbackRepository = providers.gradleProperty("centralUrl")
            .orElse(providers.environmentVariable("CENTRAL_MAVEN_URL"))
            .filter { it.isNotBlank() }
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
                val credentialsUsername = providers.gradleProperty("centralUsername")
                    .orElse(providers.environmentVariable("CENTRAL_MAVEN_USERNAME"))
                    .filter { it.isNotBlank() }
                val credentialsPassword = providers.gradleProperty("centralPassword")
                    .orElse(providers.environmentVariable("CENTRAL_MAVEN_PASSWORD"))
                    .filter { it.isNotBlank() }
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
