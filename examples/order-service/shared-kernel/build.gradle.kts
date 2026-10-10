dependencies {
    api("com.kjs.wuli3:wuli3-core:${providers.gradleProperty("wuli3.version").get()}")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
}

val sharedKernelProductionConfigurations =
    listOf("api", "implementation", "compileOnly", "compileOnlyApi", "runtimeOnly", "annotationProcessor")
val allowedSharedKernelDependencies = setOf("com.kjs.wuli3:wuli3-core", "org.jetbrains.kotlin:kotlin-stdlib")

// Kotlin 约定会注入 Kotlin 标准库；Shared Kernel 只保留边界内允许的依赖。
// 注意：必须在 afterEvaluate 中移除，约定插件注入的依赖晚于本脚本求值，懒式 withDependencies 会因依赖集提前 realize 而漏过滤。
afterEvaluate {
    sharedKernelProductionConfigurations.forEach { name ->
        configurations.getByName(name).dependencies.removeIf {
            "${it.group}:${it.name}" !in allowedSharedKernelDependencies
        }
    }
}

val verifySharedKernelDependencies = tasks.register("verifySharedKernelDependencies") {
    group = "verification"
    description = "验证 Shared Kernel 的生产依赖只包含 wuli3-core。"

    val forbiddenDependencies =
        sharedKernelProductionConfigurations
            .flatMap { configurations.getByName(it).dependencies }
            .map { "${it.group ?: "local"}:${it.name}" }
            .filterNot(allowedSharedKernelDependencies::contains)
            .distinct()
            .sorted()

    // 校验结果即任务输入；缺失声明时任务无输入，构建缓存会跨工程复用结果并掩盖真实违规。
    inputs.property("forbiddenDependencies", forbiddenDependencies)

    doLast {
        check(forbiddenDependencies.isEmpty()) {
            "Shared Kernel 包含禁止的生产依赖: ${forbiddenDependencies.joinToString()}"
        }
    }
}

tasks.named("check") {
    dependsOn(verifySharedKernelDependencies)
}
