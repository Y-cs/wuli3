package com.kjs.wuli3.it.order.acceptance;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 约束独立订单验收夹具的 DDD 依赖方向。
 *
 * @author 国杨 create on 2026/9/30 17:30
 */
@AnalyzeClasses(packages = "com.kjs.wuli3.it.order", importOptions = ImportOption.DoNotIncludeTests.class)
final class OrderArchitectureTest {
    @ArchTest
    static final ArchRule DOMAIN_HAS_NO_OUTER_DEPENDENCIES = noClasses()
            .that()
            .resideInAnyPackage("..domain..", "..sharedkernel..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..app..", "..api..", "..infra..", "..adapter..", "org.springframework..");

    @ArchTest
    static final ArchRule APP_HAS_NO_ADAPTER_DEPENDENCIES = noClasses()
            .that()
            .resideInAPackage("..app..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..infra..", "..adapter..", "org.apache.rocketmq..", "org.redisson..");

    @ArchTest
    static final ArchRule OUTPUT_PORTS_ARE_INTERFACES =
            classes().that().resideInAPackage("..app.port.out..").should().beInterfaces();

    @ArchTest
    static final ArchRule INFRA_USES_ONLY_APP_PORTS = noClasses()
            .that()
            .resideInAPackage("..infra..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..app.internal..");

    @ArchTest
    static final ArchRule HTTP_DOES_NOT_BYPASS_API = noClasses()
            .that()
            .resideInAPackage("..adapter..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..infra..", "..app..", "..domain..");
}
