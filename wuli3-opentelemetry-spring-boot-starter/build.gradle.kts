plugins {
    id("com.wulicode.wuli3.java-spring-conventions")
}

description = "OpenTelemetry Java Agent integration, trace context access, and business metrics."

dependencies {
    api(project(":wuli3-core-spring-boot-starter"))
    api("io.opentelemetry:opentelemetry-api")
}
