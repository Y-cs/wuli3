plugins {
    id("com.kjs.wuli3.java-spring-conventions")
}

dependencies {
    api(project(":wuli3-core"))
    api(project(":wuli3-context-propagation"))
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
}
