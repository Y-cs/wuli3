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
# 可按项目需要扩展事务包、仓储实现后缀和 infra -> app 契约后缀。
wuli3.ddd.transaction-review.allowed-packages=app.service,infra.persistence
wuli3.ddd.transaction-review.allowed-infrastructure-type-suffixes=RepositoryImpl
wuli3.ddd.types.infra-app-allowed-suffixes=QueryRepository,Query,DTO

# 外部服务 API 只允许 infra 引入；按实际制品和 Java 包登记，多个值用逗号分隔。
# wuli3.ddd.external-api.coordinates=com.example.customer:customer-api
# wuli3.ddd.external-api.packages=com.example.customer.api
