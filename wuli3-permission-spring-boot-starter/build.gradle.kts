plugins { id("com.kjs.wuli3.java-spring-conventions") }

description = "Permission catalog extraction and method authorization for Spring Boot."
dependencies {
    api(project(":wuli3-permission"))
    api(project(":wuli3-core-spring-boot-starter"))
    implementation("org.springframework:spring-aop")
    implementation("com.github.ben-manes.caffeine:caffeine")
}
