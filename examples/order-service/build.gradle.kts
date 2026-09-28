plugins {
    base
    id("com.kjs.wuli3.kotlin-conventions") version "0.1.1-SNAPSHOT" apply false
    id("com.kjs.wuli3.kotlin-spring-conventions") version "0.1.1-SNAPSHOT" apply false
    id("org.springframework.boot") version "3.5.15" apply false
}

allprojects {
    group = "com.example.order"
    version = "0.1.1-SNAPSHOT"
}

subprojects {
    val springModules = setOf("app", "infra", "adapter", "bootstrap")
    pluginManager.apply(
        if (name in springModules) {
            "com.kjs.wuli3.kotlin-spring-conventions"
        } else {
            "com.kjs.wuli3.kotlin-conventions"
        })
}
