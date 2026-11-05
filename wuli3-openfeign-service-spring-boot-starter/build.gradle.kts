plugins {
    id("com.kjs.wuli3.java-spring-conventions")
}

description = "为 OpenFeign 提供独立于公开 Web 出口的内部同步 HTTP 服务。"

dependencies {
    api(project(":wuli3-core-spring-boot-starter"))
    api(project(":wuli3-json"))
    api("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation(project(":wuli3-web-spring-boot-starter"))
    testImplementation(project(":wuli3-openfeign-client-spring-boot-starter"))
    testImplementation(project(":wuli3-dubbo-spring-boot-starter"))
    testImplementation("org.apache.dubbo:dubbo-spring-boot-starter")
}
