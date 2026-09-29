package com.kjs.wuli3.web.autoconfigure;

import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.accessor.AuthContextAccessor;
import com.kjs.wuli3.propagation.accessor.InvocationContextAccessor;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.propagation.store.ContextReader;
import com.kjs.wuli3.propagation.store.ContextBinder;
import com.kjs.wuli3.propagation.store.ThreadLocalContextBackend;
import com.kjs.wuli3.web.auth.AuthContextResolver;
import com.kjs.wuli3.web.context.ClientIpResolver;
import com.kjs.wuli3.web.context.RequestIdResolver;
import com.kjs.wuli3.web.context.WebContextProperties;
import com.kjs.wuli3.web.internal.auth.TrustedHttpAuthContextResolver;
import com.kjs.wuli3.web.internal.context.DefaultClientIpResolver;
import com.kjs.wuli3.web.internal.context.DefaultRequestIdResolver;
import com.kjs.wuli3.web.internal.filter.ContextFilter;
import com.kjs.wuli3.web.internal.interceptor.ContextPropagationInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;

/** Configures request context capture and propagation.
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
@AutoConfiguration
@EnableConfigurationProperties(WebContextProperties.class)
public class WebContextAutoConfiguration {

    /** 创建同时提供读取与作用域绑定能力的默认线程后端。 */
    @Bean
    @ConditionalOnMissingBean({ContextReader.class, ContextBinder.class})
    ThreadLocalContextBackend contextBinder() {
        return new ThreadLocalContextBackend();
    }

    /** 使用同一后端的读取和绑定接口创建上下文管理器。 */
    @Bean
    @ConditionalOnMissingBean
    ContextManager contextManager(final ContextReader reader, final ContextBinder binder) {
        return new ContextManager(reader, binder);
    }

    @Bean
    @ConditionalOnMissingBean
    ContextPropagator contextPropagator() {
        return new ContextPropagator(ContextPropagator.standardContextEncoder());
    }

    @Bean
    @ConditionalOnMissingBean
    InvocationContextAccessor invocationContextAccessor(final ContextReader contextReader) {
        return new InvocationContextAccessor(contextReader);
    }

    @Bean
    @ConditionalOnMissingBean
    AuthContextAccessor authContextAccessor(final ContextReader contextReader) {
        return new AuthContextAccessor(contextReader);
    }

    @Bean
    @ConditionalOnMissingBean
    AuthContextResolver authContextResolver() {
        return new TrustedHttpAuthContextResolver();
    }

    @Bean
    RestClientCustomizer wuli3InvocationContextRestClientCustomizer(
            final ContextReader contextReader, final ContextPropagator contextPropagator) {
        final ContextPropagationInterceptor interceptor =
                new ContextPropagationInterceptor(contextReader, contextPropagator);
        return builder -> builder.requestInterceptor(interceptor);
    }

    @Bean
    RestTemplateCustomizer wuli3InvocationContextRestTemplateCustomizer(
            final ContextReader contextReader, final ContextPropagator contextPropagator) {
        final ContextPropagationInterceptor interceptor =
                new ContextPropagationInterceptor(contextReader, contextPropagator);
        return restTemplate -> restTemplate.getInterceptors().add(interceptor);
    }

    @Bean
    @ConditionalOnMissingBean
    RequestIdResolver requestIdResolver(final WebContextProperties properties) {
        return new DefaultRequestIdResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    ClientIpResolver clientIpResolver(final WebContextProperties properties) {
        return new DefaultClientIpResolver(properties);
    }

    @Bean
    @ConditionalOnBean(ContextManager.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "wuli3.web.context", name = "enabled", havingValue = "true", matchIfMissing = true)
    FilterRegistrationBean<ContextFilter> contextFilterRegistration(
            final ContextManager contextManager,
            final AuthContextResolver authContextResolver,
            final RequestIdResolver requestIdResolver,
            final ClientIpResolver clientIpResolver,
            final WebContextProperties properties,
            @Qualifier("handlerExceptionResolver") final HandlerExceptionResolver exceptionResolver) {

        final ContextFilter filter =
                new ContextFilter(
                        contextManager,
                        authContextResolver,
                        requestIdResolver,
                        clientIpResolver,
                        properties,
                        exceptionResolver);
        final FilterRegistrationBean<ContextFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(properties.getFilterOrder());
        return registration;
    }
}
