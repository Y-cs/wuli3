package com.kjs.wuli3.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 验证公共服务标识的绑定优先级和配置校验。
 *
 * @author 国杨 create on 2026/10/30 16:00
 */
class ApplicationServicePropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CoreSpringBootAutoConfiguration.class));

    @Test
    void explicitServiceCodeOverridesApplicationName() {
        this.runner
                .withPropertyValues("application.service.service-code=identity", "spring.application.name=order")
                .run(context -> assertThat(context.getBean(ApplicationServiceProperties.class)
                                .getServiceCode())
                        .isEqualTo("identity"));
    }

    @Test
    void missingOrBlankServiceCodeUsesApplicationName() {
        for (final String serviceCode :
                new String[] {"", "application.service.service-code=", "application.service.service-code=   "}) {
            this.runner
                    .withPropertyValues("spring.application.name=order-service")
                    .withPropertyValues(serviceCode.isEmpty() ? new String[0] : new String[] {serviceCode})
                    .run(context -> assertThat(context.getBean(ApplicationServiceProperties.class)
                                    .getServiceCode())
                            .isEqualTo("order-service"));
        }
    }

    @Test
    void missingBothNamesKeepsOptionalMetadataEmpty() {
        this.runner.run(context -> assertThat(
                        context.getBean(ApplicationServiceProperties.class).getServiceCode())
                .isEmpty());
    }

    @Test
    void invalidEffectiveServiceCodeFailsWithoutWebValidationDependencies() {
        for (final String property : new String[] {
            "application.service.service-code=bad:name",
            "spring.application.name=bad/name",
            "application.service.service-code=" + "a".repeat(65)
        }) {
            this.runner
                    .withPropertyValues(property)
                    .run(context ->
                            assertThat(context).hasFailed().getFailure().hasStackTraceContaining("service-code"));
        }
    }
}
