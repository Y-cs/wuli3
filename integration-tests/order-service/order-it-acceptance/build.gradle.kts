dependencies {
    testImplementation(project(":order-it-bootstrap"))
    testImplementation("com.kjs.wuli3:wuli3-mysql-spring-boot-starter")
    testImplementation("com.kjs.wuli3:wuli3-redis-spring-boot-starter")
    testImplementation("com.kjs.wuli3:wuli3-rocketmq-spring-boot-starter")
    testImplementation("com.kjs.wuli3:wuli3-web-spring-boot-starter")
    testImplementation("org.apache.rocketmq:rocketmq-client-java")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
}

// 普通 check 不连接基础设施；验收必须显式调用 integrationTest。
tasks.named<Test>("test") { exclude("**/*IT.class") }
val testSourceSet = extensions.getByType<SourceSetContainer>().named("test")
tasks.register<Test>("integrationTest") {
    description = "Verifies the order fixture against real MySQL, Redis and RocketMQ v5."
    group = "verification"
    testClassesDirs = testSourceSet.get().output.classesDirs
    classpath = testSourceSet.get().runtimeClasspath
    include("**/*IT.class")
    useJUnitPlatform()
    maxParallelForks = 1
    outputs.upToDateWhen { false }
    testLogging { events("failed", "skipped"); showStandardStreams = true }
}

// 公共约定的 JaCoCo 报告依赖所有 Test；本夹具将报告限定普通测试，
// 避免 check 经报告任务间接启动需要容器的验收。
tasks.withType<org.gradle.testing.jacoco.tasks.JacocoReport>().configureEach {
    setDependsOn(listOf(tasks.named("test")))
}
tasks.withType<org.gradle.testing.jacoco.tasks.JacocoCoverageVerification>().configureEach {
    setDependsOn(listOf(tasks.named("test")))
}
