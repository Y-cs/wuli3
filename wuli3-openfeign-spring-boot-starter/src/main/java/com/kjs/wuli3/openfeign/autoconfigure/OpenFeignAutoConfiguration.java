package com.kjs.wuli3.openfeign.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kjs.wuli3.openfeign.context.FeignContextRequestInterceptor;
import com.kjs.wuli3.openfeign.error.FeignErrorDecoder;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.propagation.store.ContextBinder;
import com.kjs.wuli3.propagation.store.ContextReader;
import com.kjs.wuli3.propagation.store.ThreadLocalContextBackend;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 为 Wuli3 OpenFeign 出站调用注册上下文拦截器和错误解码器。
 *
 * <p>注意：Feign 会收集容器中所有 {@link RequestInterceptor}，因此上下文拦截器始终注册，只由
 * {@code wuli3.openfeign.context.enabled} 控制；{@link ErrorDecoder} 只允许一个，应用注册自己的 Bean 时默认实现退让。
 *
 * @author lipt create on 2026/11/4 11:20
 */
@AutoConfiguration
@ConditionalOnClass({RequestInterceptor.class, ErrorDecoder.class})
@EnableConfigurationProperties(OpenFeignProperties.class)
public class OpenFeignAutoConfiguration {

    /** 创建同时提供读取与作用域绑定能力的默认线程后端。 */
    @Bean
    @ConditionalOnMissingBean({ContextReader.class, ContextBinder.class})
    ThreadLocalContextBackend contextReader() {
        return new ThreadLocalContextBackend();
    }

    /** 创建读写标准 Wuli3 上下文字段的编码器。 */
    @Bean
    @ConditionalOnMissingBean
    ContextPropagator contextPropagator() {
        return new ContextPropagator(ContextPropagator.standardContextEncoder());
    }

    /**
     * 注册出站上下文拦截器；关闭时 Feign 请求不带 Wuli3 保留头。
     *
     * <p>注意：不使用 {@code @ConditionalOnMissingBean}，否则应用声明任意其他 Feign 拦截器都会关闭上下文传播。
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "wuli3.openfeign.context",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    RequestInterceptor wuli3FeignContextRequestInterceptor(
            final ContextReader contextReader, final ContextPropagator contextPropagator) {
        return new FeignContextRequestInterceptor(contextReader, contextPropagator);
    }

    /** 注册错误解码器；关闭、未提供 JSON 解析器或应用自带解码器时使用 Feign 默认行为。 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(ObjectMapper.class)
    @ConditionalOnProperty(
            prefix = "wuli3.openfeign.error",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    ErrorDecoder wuli3FeignErrorDecoder(final ObjectMapper objectMapper) {
        return new FeignErrorDecoder(objectMapper);
    }
}
