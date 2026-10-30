plugins {
    base
    id("com.kjs.wuli3.java-conventions") version "{{buildLogicVersion}}" apply false
    id("com.kjs.wuli3.java-spring-conventions") version "{{buildLogicVersion}}" apply false
    id("com.kjs.wuli3.ddd-conventions") version "{{buildLogicVersion}}" apply false
    id("org.springframework.boot") version "3.5.15" apply false
}

allprojects {
    group = "{{basePackage}}"
    version = "0.1.1-SNAPSHOT"
}

subprojects {
    // DDD 检查先于语言约定应用，以保持共享内核纯净。
    pluginManager.apply("com.kjs.wuli3.ddd-conventions")
    val springModules = setOf(
        "{{modulePrefix}}-app",
        "{{modulePrefix}}-infra",
        "{{modulePrefix}}-adapter",
        "{{modulePrefix}}-bootstrap",
    )
    pluginManager.apply(
        if (name in springModules) {
            "com.kjs.wuli3.java-spring-conventions"
        } else {
            "com.kjs.wuli3.java-conventions"
        })
}

// 避免开发者忘记手工执行 git config core.hooksPath：构建时把钩子目录对齐到版本化的 .githooks。
// 只在 .git 存在且当前值不一致时写入。放在任务里而不是配置阶段，否则会破坏配置缓存。
val installGitHooks = tasks.register("installGitHooks") {
    group = "wuli3"
    description = "将 git 钩子目录指向版本化的 .githooks。"
    val gitRoot = rootDir
    doLast {
        if (!gitRoot.resolve(".git").isDirectory) {
            logger.lifecycle("未找到 .git，跳过 git 钩子配置：{}", gitRoot)
            return@doLast
        }
        fun git(vararg arguments: String): String =
            runCatching {
                val process = ProcessBuilder(listOf("git") + arguments)
                    .directory(gitRoot)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.readBytes().decodeToString().trim()
                process.waitFor()
                output
            }.getOrDefault("")
        if (git("config", "--get", "core.hooksPath") != ".githooks") {
            git("config", "core.hooksPath", ".githooks")
        }
    }
}

listOf("check", "assemble").forEach { name ->
    tasks.named(name) { dependsOn(installGitHooks) }
}
