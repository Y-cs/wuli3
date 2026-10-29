package com.kjs.wuli3.openfeign.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.propagation.store.ThreadLocalContextBackend;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 验证 OpenFeign 自动配置的默认组件和配置属性绑定。
 *
 * @author lipt create on 2026/10/29 18:21
 */
class OpenFeignAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(OpenFeignAutoConfiguration.class));

    /** 默认注册上下文拦截器和错误解码器，并绑定开关属性。 */
    @Test
    void registersDefaultComponentsAndBindsSwitches() {
        this.contextRunner
                .withPropertyValues("wuli3.openfeign.context.enabled=false", "wuli3.openfeign.error.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(ThreadLocalContextBackend.class);
                    assertThat(context).hasSingleBean(ContextPropagator.class);
                    assertThat(context).doesNotHaveBean(RequestInterceptor.class);
                    assertThat(context).doesNotHaveBean(ErrorDecoder.class);
                    final OpenFeignProperties properties = context.getBean(OpenFeignProperties.class);
                    assertThat(properties.getContext().isEnabled()).isFalse();
                    assertThat(properties.getError().isEnabled()).isFalse();
                });
    }

    /** 开启状态下两类 Feign 组件均可用。 */
    @Test
    void registersFeignComponentsByDefault() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RequestInterceptor.class);
            assertThat(context).hasSingleBean(ErrorDecoder.class);
        });
    }

    /** 应用声明其他 Feign 拦截器时仍保留上下文拦截器。 */
    @Test
    void keepsContextInterceptorWhenApplicationDeclaresAnotherInterceptor() {
        this.contextRunner
                .withBean("applicationInterceptor", RequestInterceptor.class, () -> template -> {})
                .run(context -> assertThat(context).hasBean("wuli3FeignContextRequestInterceptor"));
    }
}
