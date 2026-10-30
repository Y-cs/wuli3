plugins {
    id("com.kjs.wuli3.java-spring-conventions")
}

dependencies {
    api(project(":wuli3-core-spring-boot-starter"))
    implementation(project(":wuli3-core"))
    api("com.aliyun:alibabacloud-oss-v2")
}
