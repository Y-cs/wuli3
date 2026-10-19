package com.kjs.wuli3.dubbo.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.kjs.wuli3.dubbo.context.DubboContextConsumerFilter;
import com.kjs.wuli3.dubbo.context.DubboContextProviderFilter;
import com.kjs.wuli3.dubbo.error.DubboErrorConsumerFilter;
import com.kjs.wuli3.dubbo.error.DubboErrorProviderFilter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.apache.dubbo.config.spring.extension.SpringExtensionInjector;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 验证 Dubbo SPI Filter 的 Spring 依赖能按 setter 属性名解析到。
 *
 * 注意：Dubbo 的 {@code ExtensionLoader.injectExtension} 使用 {@code SpringExtensionInjector}，按 setter 属性名
 * 查找同名 Spring Bean（见 {@code SpringExtensionInjector#getOptionalBean}）；属性名解析不到时不会回退到按类型查找，
 * 依赖为 null 的 Filter 会直接跳过传播。因此这些属性名就是 Spring 与 Dubbo 之间的注入契约。
 *
 * @author lipt create on 2026/10/16 20:20
 */
class DubboFilterSpringInjectionTest {

    private static final List<Class<?>> FILTER_TYPES = List.of(
            DubboContextConsumerFilter.class,
            DubboContextProviderFilter.class,
            DubboErrorConsumerFilter.class,
            DubboErrorProviderFilter.class);

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DubboAutoConfiguration.class));

    /** 验证四个 Filter 的每个 setter 依赖都能按属性名解析到 Spring Bean。 */
    @Test
    void resolvesEveryFilterSetterDependencyByName() {
        this.contextRunner.run(context -> {
            final SpringExtensionInjector injector = new SpringExtensionInjector();
            injector.init(context);
            final List<String> unresolved = new ArrayList<>();
            for (final Class<?> filterType : DubboFilterSpringInjectionTest.FILTER_TYPES) {
                for (final Method setter : filterType.getMethods()) {
                    if (!DubboFilterSpringInjectionTest.isSetter(setter)) {
                        continue;
                    }
                    final String property = DubboFilterSpringInjectionTest.setterProperty(setter);
                    final Object bean = injector.getInstance(setter.getParameterTypes()[0], property);
                    if (bean == null) {
                        unresolved.add(
                                filterType.getSimpleName() + "." + setter.getName() + " -> bean '" + property + "'");
                    }
                }
            }
            assertThat(unresolved).isEmpty();
        });
    }

    private static boolean isSetter(final Method method) {
        return method.getName().startsWith("set") && method.getName().length() > 3 && method.getParameterCount() == 1;
    }

    private static String setterProperty(final Method setter) {
        return Character.toLowerCase(setter.getName().charAt(3))
                + setter.getName().substring(4);
    }
}
