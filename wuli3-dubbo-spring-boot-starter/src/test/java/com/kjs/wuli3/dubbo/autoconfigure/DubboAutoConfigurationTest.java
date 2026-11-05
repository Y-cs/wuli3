package com.kjs.wuli3.dubbo.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.accessor.AuthContextAccessor;
import com.kjs.wuli3.propagation.accessor.InvocationContextAccessor;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.propagation.store.ContextBinder;
import com.kjs.wuli3.propagation.store.ContextReader;
import com.kjs.wuli3.propagation.store.ThreadLocalContextBackend;
import com.kjs.wuli3.spring.autoconfigure.ContextAutoConfiguration;
import com.kjs.wuli3.web.autoconfigure.WebContextAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 验证 Dubbo 自动配置的默认组件和配置属性绑定。
 *
 * @author GuoYang create on 2026/8/28 17:59
 */
class DubboAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DubboAutoConfiguration.class));

    /** 验证同时启用 Web、Dubbo 与 core 时只存在一套上下文基础设施。 */
    @Test
    void sharesInfrastructureWithWeb() {
        this.contextRunner
                .withConfiguration(
                        AutoConfigurations.of(WebContextAutoConfiguration.class, ContextAutoConfiguration.class))
                .withBean(
                        "handlerExceptionResolver",
                        HandlerExceptionResolver.class,
                        () -> (request, response, handler, exception) -> null)
                .run(context -> {
                    assertThat(context).hasSingleBean(ContextReader.class).hasSingleBean(ContextBinder.class);
                    assertThat(context).hasSingleBean(ContextManager.class).hasSingleBean(ContextPropagator.class);
                    assertThat(context)
                            .hasSingleBean(AuthContextAccessor.class)
                            .hasSingleBean(InvocationContextAccessor.class);
                    assertThat(context.getBean("contextReader")).isSameAs(context.getBean(ContextBinder.class));
                });
    }

    /** 验证读取与绑定使用同一个后端并允许应用替换。 */
    @Test
    void sharesCustomContextBackend() {
        final ThreadLocalContextBackend backend = new ThreadLocalContextBackend();
        this.contextRunner
                .withBean("contextReader", ThreadLocalContextBackend.class, () -> backend)
                .run(context -> {
                    assertThat(context).hasSingleBean(ContextReader.class);
                    assertThat(context).hasSingleBean(ContextBinder.class);
                    assertThat(context.getBean(ContextReader.class)).isSameAs(backend);
                    assertThat(context.getBean(ContextBinder.class)).isSameAs(backend);
                    assertThat(context).hasSingleBean(ContextManager.class);
                });
    }

    /** 验证默认上下文组件可用且各传播开关能够从外部配置绑定。 */
    @Test
    void registersDefaultContextInfrastructureAndBindsSwitches() {
        this.contextRunner
                .withPropertyValues("wuli3.dubbo.context.enabled=false", "wuli3.dubbo.error.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(ThreadLocalContextBackend.class);
                    assertThat(context).hasSingleBean(ContextManager.class);
                    assertThat(context).hasSingleBean(ContextPropagator.class);
                    final DubboProperties properties = context.getBean(DubboProperties.class);
                    assertThat(properties.getContext().isEnabled()).isFalse();
                    assertThat(properties.getError().isEnabled()).isFalse();
                });
    }
}
