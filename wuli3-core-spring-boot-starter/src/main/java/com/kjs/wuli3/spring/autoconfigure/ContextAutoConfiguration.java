package com.kjs.wuli3.spring.autoconfigure;

import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.accessor.AuthContextAccessor;
import com.kjs.wuli3.propagation.accessor.InvocationContextAccessor;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.propagation.store.ContextBinder;
import com.kjs.wuli3.propagation.store.ContextReader;
import com.kjs.wuli3.propagation.store.ThreadLocalContextBackend;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 为所有传输适配提供同一套上下文存储、访问和传播组件。
 *
 * 注意：contextReader、contextManager 和 contextPropagator 的 Bean 名也是 Dubbo SPI setter 注入契约。
 * 应用替换这些组件时须保留名称；自定义读取与绑定能力必须配套提供。
 *
 * @author 国杨 create on 2026/11/5 15:00
 */
@AutoConfiguration
public class ContextAutoConfiguration {

    /** 创建同时提供读取与绑定能力的默认线程后端。 */
    @Bean
    @ConditionalOnMissingBean({ContextReader.class, ContextBinder.class})
    ThreadLocalContextBackend contextReader() {
        return new ThreadLocalContextBackend();
    }

    /** 使用应用中唯一的一组读取与绑定能力创建管理器。 */
    @Bean
    @ConditionalOnMissingBean
    ContextManager contextManager(final ContextReader reader, final ContextBinder binder) {
        return new ContextManager(reader, binder);
    }

    /** 创建与传输协议无关的标准上下文字段编解码器。 */
    @Bean
    @ConditionalOnMissingBean
    ContextPropagator contextPropagator() {
        return new ContextPropagator(ContextPropagator.standardContextEncoder());
    }

    /** 提供调用链上下文的类型化读取入口。 */
    @Bean
    @ConditionalOnMissingBean
    InvocationContextAccessor invocationContextAccessor(final ContextReader contextReader) {
        return new InvocationContextAccessor(contextReader);
    }

    /** 提供认证上下文的类型化读取入口。 */
    @Bean
    @ConditionalOnMissingBean
    AuthContextAccessor authContextAccessor(final ContextReader contextReader) {
        return new AuthContextAccessor(contextReader);
    }
}
