plugins {
    base
}

group = "com.kjs.wuli3"
version = providers.gradleProperty("wuli3.version").get()

subprojects {
    group = rootProject.group
    version = rootProject.version
}

apply(from = "gradle/verification.gradle.kts")
