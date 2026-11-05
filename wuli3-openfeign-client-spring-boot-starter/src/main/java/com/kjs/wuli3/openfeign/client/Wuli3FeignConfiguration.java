package com.kjs.wuli3.openfeign.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kjs.wuli3.json.provider.JacksonProvider;
import com.kjs.wuli3.openfeign.client.context.HttpRpcRequestInterceptor;
import com.kjs.wuli3.openfeign.client.error.HttpRpcErrorDecoder;
import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.spring.autoconfigure.ContextAutoConfiguration;
import feign.codec.Decoder;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * 显式选择的内部 RPC 客户端配置，使用 @FeignClient(configuration = Wuli3FeignConfiguration.class)。
 *
 * 注意：不添加组件扫描注解，不作为自动配置导入根容器；仅用于受信服务的内部 RPC 端点。
 * 这里只负责装配：上下文注入、独立 JSON、错误还原及调用链约束，不接管服务发现、认证或重试策略。
 *
 * @author 国杨 create on 2026/11/5 15:00
 */
@Import(ContextAutoConfiguration.class)
public class Wuli3FeignConfiguration {
    /** 创建当前 Feign 客户端专用上下文拦截器。 */
    @Bean
    public HttpRpcRequestInterceptor httpRpcRequestInterceptor(
            final ContextManager manager, final ContextPropagator propagator) {
        return new HttpRpcRequestInterceptor(manager, propagator);
    }

    /** 创建独立 JSON 编码器，避免 Web 脱敏与资源路径展示转换。 */
    @Bean
    public Encoder httpRpcEncoder() {
        final ObjectMapper mapper = JacksonProvider.newJsonMapper();
        return (object, type, template) -> {
            try {
                template.header("Content-Type", "application/json");
                template.body(
                        mapper.writerFor(mapper.constructType(type)).writeValueAsBytes(object), StandardCharsets.UTF_8);
            } catch (final JsonProcessingException exception) {
                throw new EncodeException("Cannot encode HTTP RPC request", exception);
            }
        };
    }

    /** 创建保留泛型业务返回值的独立 JSON 解码器。 */
    @Bean
    public Decoder httpRpcDecoder() {
        final ObjectMapper mapper = JacksonProvider.newJsonMapper();
        return (response, type) -> {
            if (response.body() == null || response.status() == 204) {
                return null;
            }
            try (final InputStream input = response.body().asInputStream()) {
                return mapper.readValue(input, mapper.constructType(type));
            }
        };
    }

    /** 创建内部错误解码器。 */
    @Bean
    public HttpRpcErrorDecoder httpRpcErrorDecoder() {
        return new HttpRpcErrorDecoder();
    }

    /** 将同一解码器接入响应调用链，安装 void、dismiss404 与原始响应防绕过约束。 */
    @Bean
    public HttpRpcCapability httpRpcCapability(final HttpRpcErrorDecoder errorDecoder) {
        return new HttpRpcCapability(errorDecoder);
    }
}
