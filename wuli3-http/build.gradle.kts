plugins {
    id("com.kjs.wuli3.java-conventions")
}

dependencies {
    api(project(":wuli3-core"))
    api("org.springframework:spring-webflux")
    // ReactorClientHttpConnector 的生命周期接口依赖；不创建 Spring 容器。
    implementation("org.springframework:spring-context")
    implementation("io.projectreactor.netty:reactor-netty-http")
    // 启用原生 WebClient 的 JSON 编解码，并对显式启用的 JSON 正文日志脱敏。
    implementation("com.fasterxml.jackson.core:jackson-databind")
}
