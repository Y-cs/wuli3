plugins {
    base
    id("com.kjs.wuli3.java-conventions") apply false
    id("com.kjs.wuli3.spring-conventions") apply false
}
allprojects { group = "com.kjs.wuli3.it"; version = "0.1.0-SNAPSHOT" }
val springModules = setOf("order-it-app", "order-it-infra", "order-it-adapter", "order-it-bootstrap", "order-it-acceptance")
subprojects {
    pluginManager.apply(if (name in springModules) "com.kjs.wuli3.spring-conventions" else "com.kjs.wuli3.java-conventions")
    // 每次验收读取刚发布的 SNAPSHOT，避免历史缓存掩盖当前工作区变更。
    configurations.configureEach { resolutionStrategy.cacheChangingModulesFor(0, "seconds") }
}
tasks.named("check") { dependsOn(subprojects.map { "${it.path}:check" }) }
tasks.register("integrationTest") { dependsOn(":order-it-acceptance:integrationTest") }
