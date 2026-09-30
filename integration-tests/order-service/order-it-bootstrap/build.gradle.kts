dependencies {
    api(project(":order-it-infra"))
    api(project(":order-it-adapter"))
    implementation("org.springframework.boot:spring-boot-starter")
    runtimeOnly("com.mysql:mysql-connector-j")
}
