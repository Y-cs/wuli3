dependencies {
    api(project(":order-it-api"))
    api(project(":order-it-domain"))
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-tx")
}
