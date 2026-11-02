package com.kjs.wuli3.common

import org.gradle.api.Project
import org.gradle.api.provider.Provider

/** 将配置键与有类型的默认值绑定，避免调用方重复定义或混用默认值。 */
internal data class ConventionProperty<T : Any>(
    val key: String,
    val defaultValue: T,
)

/** 约定插件共享的配置定义；默认值仅在属性未配置时生效。 */
internal object ConventionProperties {
    val BOM_COORDINATES = ConventionProperty("wuli3.conventions.bom-coordinates", "com.kjs.wuli3:wuli3-dependencies:1.0.0")
    val FORBIDDEN_APIS_ENABLED = ConventionProperty("wuli3.conventions.forbidden-apis.enabled", true)
    val FORBIDDEN_APIS_TEST_ENABLED = ConventionProperty("wuli3.conventions.forbidden-apis.test-enabled", false)
    val JACOCO_LINE_MINIMUM = ConventionProperty("wuli3.conventions.jacoco.line.minimum", "0.45")
    val JACOCO_VERIFICATION_ENABLED = ConventionProperty("wuli3.conventions.jacoco.verification.enabled", true)
    val JAVA_VERSION = ConventionProperty("wuli3.conventions.java-version", 21)
    val LOMBOK_ENABLED = ConventionProperty("wuli3.conventions.lombok.enabled", true)
    val NULL_AWAY_ANNOTATED_PACKAGES = ConventionProperty("wuli3.conventions.nullaway.annotated-packages", "com.kjs.wuli3")
    val NULL_AWAY_ENABLED = ConventionProperty("wuli3.conventions.nullaway.enabled", true)
    val NULL_AWAY_JSPECIFY = ConventionProperty("wuli3.conventions.nullaway.jspecify", true)
    val PALANTIR_JAVA_FORMAT_VERSION = ConventionProperty("wuli3.conventions.palantir-java-format.version", "2.97.0")
    val PROJECT_BOM_PATH = ConventionProperty("wuli3.conventions.project-bom-path", ":wuli3-dependencies")
    val SPOTLESS_ENABLED = ConventionProperty("wuli3.conventions.spotless.enabled", true)
    val SPOTBUGS_ENABLED = ConventionProperty("wuli3.conventions.spotbugs.enabled", true)
    val TRANSACTION_REVIEW_ENABLED = ConventionProperty("wuli3.ddd.transaction-review.enabled", false)
    val USE_PROJECT_BOM = ConventionProperty("wuli3.conventions.use-project-bom", false)
}

/** 读取字符串配置，保留显式配置的空字符串。 */
internal fun Project.stringProperty(property: ConventionProperty<String>): String =
    stringPropertyProvider(property).get()

/** 读取布尔配置；只接受 true 或 false，非法值仍使构建失败。 */
internal fun Project.booleanProperty(property: ConventionProperty<Boolean>): Boolean =
    providers.gradleProperty(property.key).map(String::toBooleanStrict).orElse(property.defaultValue).get()

/** 读取整数配置；非法值仍使构建失败。 */
internal fun Project.intProperty(property: ConventionProperty<Int>): Int =
    providers.gradleProperty(property.key).map(String::toInt).orElse(property.defaultValue).get()

/** 为任务输入及多级回退保留 Provider 的延迟读取。 */
internal fun Project.stringPropertyProvider(property: ConventionProperty<String>): Provider<String> =
    providers.gradleProperty(property.key).orElse(property.defaultValue)
