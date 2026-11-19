dependencies {
    api(project(":{{modulePrefix}}-api"))
    api(project(":{{modulePrefix}}-domain"))
    api("com.wulicode.wuli3:wuli3-json")
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-tx")
{{appDependencies}}}
