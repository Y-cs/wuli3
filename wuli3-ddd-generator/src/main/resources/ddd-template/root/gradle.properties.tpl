org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
wuli3.version={{wuli3Version}}
wuli3.conventions.bom-coordinates=com.kjs.wuli3:wuli3-dependencies:{{wuli3Version}}
wuli3.conventions.jacoco.verification.enabled=false
wuli3.conventions.nullaway.annotated-packages={{basePackage}}

# DDD 架构边界使用单一基础包；事务位置审查按需开启。
wuli3.ddd.base-package={{basePackage}}
wuli3.ddd.transaction-review.enabled=false
