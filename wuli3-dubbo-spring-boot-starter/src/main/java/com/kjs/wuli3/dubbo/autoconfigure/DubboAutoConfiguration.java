package com.kjs.wuli3.dubbo.autoconfigure;

import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.propagation.store.ThreadLocalContextBackend;
import com.kjs.wuli3.propagation.store.ContextBinder;
import com.kjs.wuli3.propagation.store.ContextReader;
import org.apache.dubbo.rpc.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 为 Wuli3 Dubbo SPI Filter 提供可由应用覆盖的上下文基础组件。
 *
 * 注意：Filter 实例由 Dubbo 扩展机制创建，这些 Spring Bean 会通过 Dubbo 的 SpringExtensionFactory 注入。
 *
 * @author GuoYang create on 2026/8/28 17:59
 */
@AutoConfiguration
@ConditionalOnClass(Filter.class)
@EnableConfigurationProperties(DubboProperties.class)
public class DubboAutoConfiguration {
    /** 创建同时提供读取与作用域绑定能力的默认线程后端。 */
    @Bean
    @ConditionalOnMissingBean({ContextReader.class, ContextBinder.class})
    ThreadLocalContextBackend dubboContextBinder() {
        return new ThreadLocalContextBackend();
    }

    /** 使用同一后端的读取和绑定接口创建上下文管理器。 */
    @Bean
    @ConditionalOnMissingBean
    ContextManager dubboContextManager(final ContextReader reader, final ContextBinder binder) {
        return new ContextManager(reader, binder);
    }

    /** 创建读写 Dubbo attachments 的标准上下文字段编码器。 */
    @Bean
    @ConditionalOnMissingBean
    ContextPropagator dubboContextEncoder() {
        return new ContextPropagator(ContextPropagator.standardContextEncoder());
    }
}
