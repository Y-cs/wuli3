plugins {
    id("com.kjs.wuli3.java-spring-conventions")
}

description = "显式选择的 OpenFeign 内部调用客户端。"

dependencies {
    api(project(":wuli3-json"))
    api(project(":wuli3-core-spring-boot-starter"))
    api(project(":wuli3-context-propagation"))
    api("org.springframework.cloud:spring-cloud-starter-openfeign")
}
