package com.kjs.wuli3.java

import com.kjs.wuli3.common.ConventionProperties
import com.kjs.wuli3.common.booleanProperty
import com.kjs.wuli3.common.stringProperty
import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsExtension
import com.github.spotbugs.snom.SpotBugsTask
import com.diffplug.gradle.spotless.SpotlessExtension
import de.thetaphi.forbiddenapis.gradle.CheckForbiddenApis
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.Project
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import java.net.URL

/** Java 构建入口内部使用的质量配置，统一注册质量工具及其开关。 */
internal class JavaQualityConfigurer {
    /** 为已应用 Java 插件的项目配置质量门禁。 */
    fun configure(project: Project) {
        with(project) {
            pluginManager.apply("checkstyle")
            pluginManager.apply("com.diffplug.spotless")
            pluginManager.apply("com.github.spotbugs")
            pluginManager.apply("de.thetaphi.forbiddenapis")
            pluginManager.apply("net.ltgt.errorprone")

            val forbiddenApisEnabled = booleanProperty(ConventionProperties.FORBIDDEN_APIS_ENABLED)
            val forbiddenApisTestEnabled = booleanProperty(ConventionProperties.FORBIDDEN_APIS_TEST_ENABLED)
            val nullAwayEnabled = booleanProperty(ConventionProperties.NULL_AWAY_ENABLED)
            val nullAwayJSpecify = booleanProperty(ConventionProperties.NULL_AWAY_JSPECIFY)
            val spotlessEnabled = booleanProperty(ConventionProperties.SPOTLESS_ENABLED)
            val spotBugsEnabled = booleanProperty(ConventionProperties.SPOTBUGS_ENABLED)
            val nullAwayAnnotatedPackages = stringProperty(ConventionProperties.NULL_AWAY_ANNOTATED_PACKAGES)
            val palantirJavaFormatVersion = stringProperty(ConventionProperties.PALANTIR_JAVA_FORMAT_VERSION)

            extensions.configure<CheckstyleExtension> {
                toolVersion = "13.6.0"
                config = resources.text.fromString(pluginResource("checkstyle/checkstyle.xml").readText())
                isShowViolations = true
            }

            extensions.configure<SpotBugsExtension> {
                toolVersion.set("4.10.2")
                effort.set(Effort.MAX)
                reportLevel.set(Confidence.HIGH)
                ignoreFailures.set(false)
                showStackTraces.set(true)
            }

            extensions.configure<SpotlessExtension> {
                java {
                    target("src/*/java/**/*.java")
                    palantirJavaFormat(palantirJavaFormatVersion)
                    trimTrailingWhitespace()
                    endWithNewline()
                }
                kotlin {
                    target("src/*/kotlin/**/*.kt")
                    ktfmt("0.54").kotlinlangStyle()
                    trimTrailingWhitespace()
                    endWithNewline()
                }
            }

            dependencies {
                // Error Prone 配置会合入各 source set 的 annotationProcessor，由 Java 入口导入的同一 BOM 管理版本。
                "errorprone"("com.google.errorprone:error_prone_core")
                "errorprone"("com.uber.nullaway:nullaway")
                "compileOnly"("org.jspecify:jspecify")
            }

            tasks.withType<Checkstyle>().configureEach {
                reports {
                    xml.required.set(true)
                    html.required.set(true)
                }
            }

            tasks.withType<SpotBugsTask>().configureEach {
                enabled = spotBugsEnabled
                reports.create("xml") {
                    required.set(true)
                }
                reports.create("html") {
                    required.set(true)
                }
            }

            tasks.matching { it.name.startsWith("spotless") }.configureEach {
                enabled = spotlessEnabled
            }

            tasks.withType<CheckForbiddenApis>().configureEach {
                enabled = forbiddenApisEnabled
                bundledSignatures.addAll(listOf("jdk-unsafe"))
                signaturesURLs.add(pluginResource("forbidden-apis/wuli3-signatures.txt"))
                ignoreFailures = false
            }

            tasks.matching { it.name == "forbiddenApisTest" }.configureEach {
                enabled = forbiddenApisEnabled && forbiddenApisTestEnabled
            }

            tasks.withType<JavaCompile>().configureEach {
                options.errorprone {
                    disableWarningsInGeneratedCode.set(true)
                    check(
                        "NullAway",
                        if (nullAwayEnabled) CheckSeverity.ERROR else CheckSeverity.OFF,
                    )
                    if (nullAwayEnabled) {
                        option("NullAway:AnnotatedPackages", nullAwayAnnotatedPackages)
                        option("NullAway:JSpecifyMode", nullAwayJSpecify.toString())
                        option(
                            "NullAway:ExcludedFieldAnnotations",
                            "org.apache.dubbo.config.annotation.DubboReference",
                        )
                    }
                }
            }
        }
    }

    private fun pluginResource(path: String): URL =
        requireNotNull(javaClass.classLoader.getResource(path)) {
            "Missing wuli3-build-logic resource: $path"
        }
}
