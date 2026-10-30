plugins {
    id("com.kjs.wuli3.java-spring-conventions")
}

dependencies {
    api(project(":wuli3-core-spring-boot-starter"))
    api(project(":wuli3-pagination"))
    api("com.baomidou:mybatis-plus-spring-boot3-starter")
    api("com.baomidou:mybatis-plus-jsqlparser-4.9")
    runtimeOnly("com.mysql:mysql-connector-j")
}
