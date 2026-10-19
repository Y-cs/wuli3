package com.kjs.wuli3.dubbo.autoconfigure;

import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.propagation.store.ContextBinder;
import com.kjs.wuli3.propagation.store.ContextReader;
import com.kjs.wuli3.propagation.store.ThreadLocalContextBackend;
import org.apache.dubbo.rpc.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 为 Wuli3 Dubbo SPI Filter 提供可由应用覆盖的上下文基础组件。
 *
 * 注意：Filter 实例由 Dubbo 扩展机制创建，依赖通过 Dubbo 的 Spring 扩展注入写入。该注入按 setter 属性名查找同名
 * Spring Bean，属性名解析不到时不会回退到按类型查找，Filter 会因依赖为 null 静默跳过传播。因此本类注册的 Bean 名
 * 必须是 dubboProperties、contextReader、contextManager、contextPropagator，与 Filter 的 setter 属性名严格一致；
 * 重命名这些 Bean 会直接关闭传播。契约由 DubboFilterSpringInjectionTest 守护。
 *
 * 注意：此处只用 {@code @EnableConfigurationProperties} 启用属性绑定基础设施，属性 Bean 由 {@link #dubboProperties()}
 * 按注入契约命名注册。
 *
 * @author GuoYang create on 2026/8/28 17:59
 */
@AutoConfiguration
@ConditionalOnClass(Filter.class)
@EnableConfigurationProperties
public class DubboAutoConfiguration {

    /** 创建 Dubbo 传播开关属性；Bean 名须为 dubboProperties 才能注入 Filter。 */
    @Bean
    @ConditionalOnMissingBean
    DubboProperties dubboProperties() {
        return new DubboProperties();
    }

    /** 创建同时提供读取与作用域绑定能力的默认线程后端；Bean 名须为 contextReader 才能注入 consumer Filter。 */
    @Bean
    @ConditionalOnMissingBean({ContextReader.class, ContextBinder.class})
    ThreadLocalContextBackend contextReader() {
        return new ThreadLocalContextBackend();
    }

    /** 使用同一后端的读取和绑定接口创建上下文管理器；Bean 名须为 contextManager 才能注入 provider Filter。 */
    @Bean
    @ConditionalOnMissingBean
    ContextManager contextManager(final ContextReader reader, final ContextBinder binder) {
        return new ContextManager(reader, binder);
    }

    /** 创建读写 Dubbo attachments 的标准上下文字段编码器；Bean 名须为 contextPropagator 才能注入 Filter。 */
    @Bean
    @ConditionalOnMissingBean
    ContextPropagator contextPropagator() {
        return new ContextPropagator(ContextPropagator.standardContextEncoder());
    }
}
