package com.kjs.wuli3.dubbo.autoconfigure;

import com.kjs.wuli3.spring.autoconfigure.ContextAutoConfiguration;
import org.apache.dubbo.rpc.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * 为 Wuli3 Dubbo SPI Filter 提供可由应用覆盖的上下文基础组件。
 *
 * 注意：Filter 实例由 Dubbo 扩展机制创建，依赖通过 Dubbo 的 Spring 扩展注入写入。该注入按 setter 属性名查找同名
 * Spring Bean，属性名解析不到时不会回退到按类型查找，Filter 会因依赖为 null 静默跳过传播。因此本类及共享上下文配置注册的 Bean 名
 * 必须是 dubboProperties、contextReader、contextManager、contextPropagator，与 Filter 的 setter 属性名严格一致；
 * 重命名这些 Bean 会直接关闭传播。契约由 DubboFilterSpringInjectionTest 守护。
 *
 * 注意：此处只用 {@code @EnableConfigurationProperties} 启用属性绑定基础设施，属性 Bean 由 {@link #dubboProperties()}
 * 按注入契约命名注册。
 *
 * @author GuoYang create on 2026/8/28 17:59
 */
@AutoConfiguration(after = ContextAutoConfiguration.class)
@Import(ContextAutoConfiguration.class)
@ConditionalOnClass(Filter.class)
@EnableConfigurationProperties
public class DubboAutoConfiguration {

    /** 创建 Dubbo 传播开关属性；Bean 名须为 dubboProperties 才能注入 Filter。 */
    @Bean
    @ConditionalOnMissingBean
    DubboProperties dubboProperties() {
        return new DubboProperties();
    }
}
