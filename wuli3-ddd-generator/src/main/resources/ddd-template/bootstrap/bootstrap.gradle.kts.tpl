plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":infra"))
    implementation(project(":adapter"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
{{bootstrapDependencies}}
}

springBoot {
    mainClass.set("{{basePackage}}.BootstrapApplication")
}

// 可执行 jar 固定为模块名，不带版本号，便于 Dockerfile 引用确定的文件名。
tasks.bootJar {
    archiveFileName.set("bootstrap.jar")
}
