package com.kjs.wuli3.ddd

/** 声明与字节码使用同一可达依赖矩阵，避免 Gradle 的 API 传递依赖被误判。 */
internal object DddBoundaries {
    val allowedModules: Map<String, Set<String>> = mapOf(
        "shared-kernel" to emptySet(),
        "domain" to setOf("shared-kernel"),
        "api" to setOf("shared-kernel"),
        "app" to setOf("api", "domain", "shared-kernel"),
        "infra" to setOf("app", "domain", "api", "shared-kernel"),
        "adapter" to setOf("app", "api", "domain", "shared-kernel"),
        "bootstrap" to setOf("adapter", "infra", "app", "api", "domain", "shared-kernel"),
    )

    /**
     * 解析模块角色；生成工程使用 `<service>-<角色>` 命名，标准角色名保持兼容。
     */
    fun roleOf(moduleName: String): String? =
        allowedModules.keys.firstOrNull { moduleName == it || moduleName.endsWith("-$it") }

    fun isSpringGroup(group: String?): Boolean =
        group == "org.springframework" || group?.startsWith("org.springframework.") == true
}
