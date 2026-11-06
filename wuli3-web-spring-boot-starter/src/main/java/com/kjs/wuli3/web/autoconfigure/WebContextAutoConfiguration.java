package com.kjs.wuli3.web.autoconfigure;

import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.accessor.AuthContextAccessor;
import com.kjs.wuli3.spring.autoconfigure.ContextAutoConfiguration;
import com.kjs.wuli3.web.auth.AuthContextResolver;
import com.kjs.wuli3.web.auth.LoginRequired;
import com.kjs.wuli3.web.context.ClientIpResolver;
import com.kjs.wuli3.web.context.RequestIdResolver;
import com.kjs.wuli3.web.context.WebContextProperties;
import com.kjs.wuli3.web.internal.auth.TrustedHttpAuthContextResolver;
import com.kjs.wuli3.web.internal.context.DefaultClientIpResolver;
import com.kjs.wuli3.web.internal.context.DefaultRequestIdResolver;
import com.kjs.wuli3.web.internal.filter.ContextFilter;
import com.kjs.wuli3.web.internal.interceptor.LoginRequiredInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Configures request context capture and propagation.
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
@AutoConfiguration(after = ContextAutoConfiguration.class)
@Import(ContextAutoConfiguration.class)
@EnableConfigurationProperties(WebContextProperties.class)
public class WebContextAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AuthContextResolver authContextResolver() {
        return new TrustedHttpAuthContextResolver();
    }

    /**
     * 注册登录校验拦截器；接口通过 {@link LoginRequired} 显式声明。
     *
     * 注意：与上下文过滤器共用启用开关，上下文关闭时不注册，避免标记接口恒被拒绝。
     */
    @Bean
    @ConditionalOnProperty(prefix = "wuli3.web.context", name = "enabled", havingValue = "true", matchIfMissing = true)
    WebMvcConfigurer wuli3LoginRequiredWebMvcConfigurer(final AuthContextAccessor authContextAccessor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(final InterceptorRegistry registry) {
                registry.addInterceptor(new LoginRequiredInterceptor(authContextAccessor));
            }
        };
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

        final ContextFilter filter = new ContextFilter(
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
