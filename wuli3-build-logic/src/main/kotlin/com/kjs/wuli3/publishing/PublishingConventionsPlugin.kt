package com.kjs.wuli3.publishing

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.withType

/** Maven publication conventions for public Wuli3 Java components. */
class PublishingConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            pluginManager.apply("maven-publish")
            val isGradlePluginProject = pluginManager.hasPlugin("java-gradle-plugin")

            extensions.configure<JavaPluginExtension> {
                withJavadocJar()
            }

            tasks.withType<Javadoc>().configureEach {
                (options as StandardJavadocDocletOptions).apply {
                    addBooleanOption("Xdoclint:all,-missing", true)
                    addBooleanOption("Werror", true)
                }
            }

            extensions.configure<PublishingExtension> {
                publications {
                    if (!isGradlePluginProject) {
                        create<MavenPublication>("mavenJava") {
                            from(components["java"])
                            pom {
                                name.set(project.name)
                                description.set(project.description ?: "Wuli3 component ${project.name}")
                                url.set("https://github.com/kjs/wuli3")
                                licenses {
                                    license {
                                        name.set("Apache License, Version 2.0")
                                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                                    }
                                }
                                scm {
                                    connection.set("scm:git:https://github.com/kjs/wuli3.git")
                                    developerConnection.set("scm:git:ssh://git@github.com/kjs/wuli3.git")
                                    url.set("https://github.com/kjs/wuli3")
                                }
                                developers {
                                    developer {
                                        id.set("wuli3-maintainers")
                                        name.set("Wuli3 Maintainers")
                                    }
                                }
                            }
                        }
                    }
                }
                repositories {
                    maven {
                        name = "temporary"
                        url = rootProject.layout.buildDirectory.dir("temporary-maven-repository").get().asFile.toURI()
                    }
                    centralMaven(project)
                }
            }
        }
    }
}

/**
 * 注册 central Maven 制品仓库，用于发布到内部仓库
 */
private fun RepositoryHandler.centralMaven(project: Project) {
    val releases = project.stringPropertyOrEnv("centralReleasesUrl", "CENTRAL_MAVEN_RELEASES_URL")
    val snapshots = project.stringPropertyOrEnv("centralSnapshotsUrl", "CENTRAL_MAVEN_SNAPSHOTS_URL")
    val fallback = project.stringPropertyOrEnv("centralUrl", "CENTRAL_MAVEN_URL")
    val repositoryUrl = if (project.version.toString().endsWith("-SNAPSHOT")) {
        snapshots.orElse(fallback)
    } else {
        releases.orElse(fallback)
    }
    if (!repositoryUrl.isPresent) {
        return
    }
    maven {
        name = "central"
        url = project.uri(repositoryUrl.get())
        // 凭据缺失时不注册 credentials 块，否则 Gradle 会因 username 未赋值而校验失败；
        // 此时按匿名发布处理，由仓库端决定是否拒绝。
        val credentialsUsername = project.stringPropertyOrEnv("centralUsername", "CENTRAL_MAVEN_USERNAME")
        val credentialsPassword = project.stringPropertyOrEnv("centralPassword", "CENTRAL_MAVEN_PASSWORD")
        if (credentialsUsername.isPresent && credentialsPassword.isPresent) {
            credentials {
                username = credentialsUsername.get()
                password = credentialsPassword.get()
            }
        }
    }
}

/** 读取同名 Gradle 属性，缺失时回退到指定的环境变量；空值按未配置处理。 */
internal fun Project.stringPropertyOrEnv(propertyName: String, environmentVariableName: String): Provider<String> =
    providers.gradleProperty(propertyName)
        .orElse(providers.environmentVariable(environmentVariableName))
        .filter { it.isNotBlank() }
