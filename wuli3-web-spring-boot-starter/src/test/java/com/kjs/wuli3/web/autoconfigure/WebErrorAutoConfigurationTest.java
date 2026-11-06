package com.kjs.wuli3.web.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.core.error.resolver.ErrorResolver;
import com.kjs.wuli3.propagation.accessor.InvocationContextAccessor;
import com.kjs.wuli3.spring.autoconfigure.ApplicationServiceProperties;
import com.kjs.wuli3.spring.autoconfigure.CoreSpringBootAutoConfiguration;
import com.kjs.wuli3.web.error.WebErrorStatusContext;
import com.kjs.wuli3.web.error.WebErrorStatusCustomizer;
import com.kjs.wuli3.web.internal.handler.WebExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.error.ErrorAttributes;

/**
 * 验证 Web 错误码的服务标识规则和可选状态定制器装配。
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

    /** 未提供定制器时直接装配默认错误出口，不创建默认定制器 Bean。 */
    @Test
    void defaultHandlerDoesNotRequireAStatusCustomizer() {
        this.responseRunner().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(WebExceptionHandler.class);
            assertThat(context).doesNotHaveBean(WebErrorStatusCustomizer.class);
        });
    }

    /** 单个应用定制器无需声明为 Primary 即可参与装配。 */
    @Test
    void singleStatusCustomizerIsOptionalAndUnambiguous() {
        this.responseRunner()
                .withBean(WebErrorStatusCustomizer.class, () -> WebErrorStatusContext::defaultStatus)
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(WebExceptionHandler.class)
                        .hasSingleBean(WebErrorStatusCustomizer.class));
    }

    /** 多个无主定制器属于装配歧义，应显式失败，不能静默跳过应用策略。 */
    @Test
    void ambiguousStatusCustomizersFailConfiguration() {
        this.responseRunner()
                .withBean("first", WebErrorStatusCustomizer.class, () -> WebErrorStatusContext::defaultStatus)
                .withBean("second", WebErrorStatusCustomizer.class, () -> WebErrorStatusContext::defaultStatus)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class));
    }

    /** 提供错误出口必需的协作对象，单独验证可选扩展的装配契约。 */
    private ApplicationContextRunner responseRunner() {
        return this.runner
                .withConfiguration(AutoConfigurations.of(WebResponseAutoConfiguration.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(InvocationContextAccessor.class, () -> mock(InvocationContextAccessor.class))
                .withBean(ErrorAttributes.class, () -> mock(ErrorAttributes.class));
    }
}
