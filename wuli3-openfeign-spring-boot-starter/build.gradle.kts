plugins {
    id("com.kjs.wuli3.java-spring-conventions")
}

dependencies {
    api(project(":wuli3-core"))
    api(project(":wuli3-context-propagation"))
    api("org.springframework.cloud:spring-cloud-starter-openfeign")
    compileOnly("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
}
