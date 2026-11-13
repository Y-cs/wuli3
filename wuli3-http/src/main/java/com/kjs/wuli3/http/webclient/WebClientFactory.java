package com.kjs.wuli3.http.webclient;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.http.internal.logging.HttpLogs;
import com.kjs.wuli3.http.logging.HttpLogEvent;
import com.kjs.wuli3.http.logging.HttpLogRecorder;
import io.netty.channel.ChannelOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * 显式装配原生 WebClient，通过单个外层过滤器维护请求生命周期。
 *
 * 注意：构建器只用于装配，不支持并发修改；构建结果可并发复用，无需 Spring 容器。
 *
 * @author 国杨 create on 2026/11/13 15:40
 */
public final class WebClientFactory {

    private WebClientFactory() {}

    /** 使用不可变配置创建装配入口。 */
    public static Builder builder(final WebClientConfig config) {
        Asserts.whenNull(config).throwIllegalArgumentException("客户端配置不能为空");
        return new Builder(config);
    }

    /** 保留原生能力的装配器；同类回调按注册顺序执行。 */
    public static final class Builder {

        private final WebClientConfig config;
        private final List<Consumer<InitializationContext>> initializers = new ArrayList<>();
        private final List<BiFunction<HttpExchangeContext, ClientRequest, Mono<ClientRequest>>> before =
                new ArrayList<>();
        private final List<BiFunction<HttpExchangeContext, ClientResponse, Mono<ClientResponse>>> after =
                new ArrayList<>();
        private final List<BiConsumer<HttpExchangeContext, Throwable>> errors = new ArrayList<>();
        private final List<BiConsumer<InitializationContext, Throwable>> initializationErrors = new ArrayList<>();
        private HttpLogRecorder recorder = HttpLogs.defaultRecorder();
        private @Nullable ClientHttpConnector connector;

        private Builder(final WebClientConfig config) {
            this.config = config;
        }

        /**
         * 注册应用默认配置后的构建回调；每次 build 按注册顺序执行一次。
         *
         * 注意：通过 context.builder() 定制原生能力；生命周期过滤器在全部初始化回调之后装配到最外层。
         */
        public Builder initialize(final Consumer<InitializationContext> initializer) {
            Asserts.whenNull(initializer).throwIllegalArgumentException("初始化回调不能为空");
            this.initializers.add(initializer);
            return this;
        }

        /** 注册异步请求转换；返回空 Mono 或 null 视为失败。 */
        public Builder beforeRequest(
                final BiFunction<HttpExchangeContext, ClientRequest, Mono<ClientRequest>> processor) {
            Asserts.whenNull(processor).throwIllegalArgumentException("请求回调不能为空");
            this.before.add(processor);
            return this;
        }

        /**
         * 注册收到响应头后的异步转换。
         *
         * 注意：消费或替换正文时，由回调负责有界聚合并释放旧正文；不得遗弃旧响应资源。
         */
        public Builder afterResponse(
                final BiFunction<HttpExchangeContext, ClientResponse, Mono<ClientResponse>> processor) {
            Asserts.whenNull(processor).throwIllegalArgumentException("响应回调不能为空");
            this.after.add(processor);
            return this;
        }

        /** 注册 HTTP 阶段错误观察器；观察器异常附加到原始异常，不替换原始失败。 */
        public Builder onError(final BiConsumer<HttpExchangeContext, Throwable> handler) {
            Asserts.whenNull(handler).throwIllegalArgumentException("错误回调不能为空");
            this.errors.add(handler);
            return this;
        }

        /** 注册构建失败观察器；构建异常继续向调用方抛出。 */
        public Builder onInitializationError(final BiConsumer<InitializationContext, Throwable> handler) {
            Asserts.whenNull(handler).throwIllegalArgumentException("初始化错误回调不能为空");
            this.initializationErrors.add(handler);
            return this;
        }

        /** 设置初始化与全部请求共享的日志接收器；实现须支持并发调用。 */
        public Builder logRecorder(final HttpLogRecorder recorder) {
            Asserts.whenNull(recorder).throwIllegalArgumentException("日志接收器不能为空");
            this.recorder = recorder;
            return this;
        }

        /** 使用调用方管理资源与超时的连接器替换默认传输实现。 */
        public Builder connector(final ClientHttpConnector value) {
            Asserts.whenNull(value).throwIllegalArgumentException("连接器不能为空");
            this.connector = value;
            return this;
        }

        /** 构建并返回原生客户端；默认使用共享 Reactor Netty 资源，关闭自动重试和重定向。 */
        public WebClient build() {
            final WebClient.Builder builder = WebClient.builder();
            final WebClientConfig config = this.config;
            final HttpLogRecorder recorder = HttpLogs.safeRecorder(config.logging(), this.recorder);
            final InitializationContext context = new InitializationContext(this.config, builder, recorder);
            try {
                final ClientHttpConnector configuredConnector = this.connector;
                builder.clientConnector(configuredConnector == null ? this.defaultConnector() : configuredConnector);
                final String baseUrl = this.config.baseUrl();
                if (baseUrl != null) {
                    builder.baseUrl(baseUrl);
                }
                builder.defaultHeaders(headers -> headers.addAll(this.config.defaultHeaders()));
                builder.codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(this.config.maxInMemorySize()));
                this.initializers.forEach(initializer -> initializer.accept(context));
                final List<BiFunction<HttpExchangeContext, ClientRequest, Mono<ClientRequest>>> beforeCallbacks =
                        List.copyOf(this.before);
                final List<BiFunction<HttpExchangeContext, ClientResponse, Mono<ClientResponse>>> afterCallbacks =
                        List.copyOf(this.after);
                final List<BiConsumer<HttpExchangeContext, Throwable>> errorCallbacks = List.copyOf(this.errors);
                builder.filters(filters -> filters.addFirst((request, next) -> Mono.defer(
                        () -> new HttpExchange(config, recorder, beforeCallbacks, afterCallbacks, errorCallbacks)
                                .exchange(request, next))));
                final WebClient client = builder.build();
                recorder.record(HttpLogEvent.INITIALIZED, Map.of("client", this.config.name()));
                return client;
            } catch (final RuntimeException | Error exception) {
                this.initializationErrors.forEach(handler -> {
                    try {
                        handler.accept(context, exception);
                    } catch (final RuntimeException | Error observerFailure) {
                        HttpLogs.suppress(exception, observerFailure);
                    }
                });
                recorder.record(
                        HttpLogEvent.INITIALIZATION_ERROR,
                        Map.of(
                                "client",
                                this.config.name(),
                                "error",
                                exception.getClass().getName()));
                throw exception;
            }
        }

        private ClientHttpConnector defaultConnector() {
            return new ReactorClientHttpConnector(HttpClient.create()
                    .option(
                            ChannelOption.CONNECT_TIMEOUT_MILLIS,
                            Math.toIntExact(this.config.connectTimeout().toMillis()))
                    .responseTimeout(this.config.responseTimeout())
                    .disableRetry(true)
                    .followRedirect(false));
        }
    }
}
