package com.kjs.wuli3.web.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.core.error.resolver.ErrorResolver;
import com.kjs.wuli3.spring.autoconfigure.ApplicationServiceProperties;
import com.kjs.wuli3.spring.autoconfigure.CoreSpringBootAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 验证 Web 错误码复用公共服务标识及应用名回退规则。
 *
 * @author 国杨 create on 2026/10/30 17:36
 */
class WebErrorAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(CoreSpringBootAutoConfiguration.class, WebErrorAutoConfiguration.class));

    @Test
    void errorCodesUseTheApplicationNameWhenServiceCodeIsBlank() {
        this.runner
                .withPropertyValues("spring.application.name=order-service", "application.service.service-code=   ")
                .run(context -> {
                    assertThat(context).hasSingleBean(ApplicationServiceProperties.class);
                    assertThat(context.getBean(ErrorResolver.class).resolveCode(CommonErrors.ILLEGAL_ARGUMENT))
                            .isEqualTo("ORDER-SERVICE.COMMON.ILLEGAL_ARGUMENT");
                });
    }

    @Test
    void explicitServiceCodeOverridesApplicationNameForErrorCodes() {
        this.runner
                .withPropertyValues(
                        "spring.application.name=order-service", "application.service.service-code=public-api")
                .run(context -> assertThat(
                                context.getBean(ErrorResolver.class).resolveCode(CommonErrors.ILLEGAL_ARGUMENT))
                        .isEqualTo("PUBLIC-API.COMMON.ILLEGAL_ARGUMENT"));
    }
}
