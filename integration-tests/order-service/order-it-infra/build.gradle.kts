dependencies {
    api(project(":order-it-app"))
    implementation("com.kjs.wuli3:wuli3-mysql-spring-boot-starter")
    implementation("com.kjs.wuli3:wuli3-redis-spring-boot-starter")
    implementation("com.kjs.wuli3:wuli3-rocketmq-spring-boot-starter")
    implementation("org.apache.rocketmq:rocketmq-client-java")
}
