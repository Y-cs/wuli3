package com.kjs.wuli3.http.webclient;

import com.kjs.wuli3.http.internal.logging.HttpLogs;
import com.kjs.wuli3.http.logging.HttpLogEvent;
import com.kjs.wuli3.http.logging.HttpLogRecorder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * 串联单次订阅的回调，维护响应资源交接并记录唯一终结摘要。
 *
 * @author 国杨 create on 2026/11/13 15:40
 */
final class HttpExchange {

    private final WebClientConfig config;
    private final HttpExchangeContext context;
    private final List<BiFunction<HttpExchangeContext, ClientRequest, Mono<ClientRequest>>> before;
    private final List<BiFunction<HttpExchangeContext, ClientResponse, Mono<ClientResponse>>> after;
    private final List<BiConsumer<HttpExchangeContext, Throwable>> errors;
    private final long started = System.nanoTime();
    private final AtomicBoolean finished = new AtomicBoolean();
    // 响应交付后由正文负责终结，外层 Mono 的取消不再结束本次调用。
    private volatile boolean delivered;

    HttpExchange(
            final WebClientConfig config,
            final HttpLogRecorder recorder,
            final List<BiFunction<HttpExchangeContext, ClientRequest, Mono<ClientRequest>>> before,
            final List<BiFunction<HttpExchangeContext, ClientResponse, Mono<ClientResponse>>> after,
            final List<BiConsumer<HttpExchangeContext, Throwable>> errors) {
        this.config = config;
        this.context = new HttpExchangeContext(config.name(), recorder);
        this.before = before;
        this.after = after;
        this.errors = errors;
    }

    Mono<ClientResponse> exchange(final ClientRequest request, final ExchangeFunction next) {
        Mono<ClientRequest> prepared = Mono.just(request);
        for (final var processor : this.before) {
            prepared = prepared.flatMap(value -> Mono.defer(() -> processor.apply(this.context, value))
                    .switchIfEmpty(Mono.error(new IllegalStateException("请求回调不能返回空 Mono"))));
        }
        return prepared.flatMap(value -> {
                    this.requestLog(value);
                    return Mono.defer(() -> next.exchange(value));
                })
                .flatMap(this::processResponse)
                .switchIfEmpty(Mono.error(new IllegalStateException("HTTP 交换未返回响应")))
                .doOnNext(response -> this.delivered = true)
                .doOnError(failure -> this.finish(SignalType.ON_ERROR, failure))
                .doFinally(signal -> {
                    if (signal == SignalType.CANCEL && !this.delivered) {
                        this.finish(signal, null);
                    }
                });
    }

    private Mono<ClientResponse> processResponse(final ClientResponse response) {
        this.responseLog(response);
        Mono<ClientResponse> processed = Mono.just(response);
        for (final var processor : this.after) {
            processed = processed.flatMap(value -> this.processAfter(value, processor));
        }
        return processed.map(value -> value.mutate()
                .body(body -> body.doOnError(failure -> this.finish(SignalType.ON_ERROR, failure))
                        .doFinally(signal -> this.finish(signal, null)))
                .build());
    }

    private Mono<ClientResponse> processAfter(
            final ClientResponse response,
            final BiFunction<HttpExchangeContext, ClientResponse, Mono<ClientResponse>> processor) {
        final AtomicBoolean bodySubscribed = new AtomicBoolean();
        final ClientResponse guarded = response.mutate()
                .body(body -> body.doOnSubscribe(ignored -> bodySubscribed.set(true)))
                .build();
        // 失败或取消时清理尚未消费的响应；已开始消费的正文由回调链负责释放。
        return Mono.usingWhen(
                Mono.just(guarded),
                current -> Mono.defer(() -> processor.apply(this.context, current))
                        .switchIfEmpty(Mono.error(new IllegalStateException("响应回调不能返回空 Mono"))),
                current -> Mono.empty(),
                (current, failure) -> bodySubscribed.get() ? Mono.empty() : HttpExchange.discard(current),
                current -> bodySubscribed.get() ? Mono.empty() : HttpExchange.discard(current));
    }

    private static Mono<Void> discard(final ClientResponse response) {
        // 订阅后立即取消，避免清理无限流时等待正文结束。
        return response.bodyToFlux(DataBuffer.class)
                .take(0, false)
                .doOnDiscard(DataBuffer.class, DataBufferUtils::release)
                .then()
                .onErrorComplete();
    }

    private void finish(final SignalType signal, final @Nullable Throwable failure) {
        if (!this.finished.compareAndSet(false, true)) {
            return;
        }
        if (failure != null) {
            for (final var handler : this.errors) {
                try {
                    handler.accept(this.context, failure);
                } catch (final RuntimeException | Error observerFailure) {
                    HttpLogs.suppress(failure, observerFailure);
                }
            }
        }
        if (this.config.logging().enabled()) {
            final Map<String, Object> fields = this.fields();
            fields.put("outcome", failure != null ? "error" : signal == SignalType.CANCEL ? "cancelled" : "complete");
            fields.put("elapsedNanos", System.nanoTime() - this.started);
            if (failure != null) {
                fields.put("error", failure.getClass().getName());
            }
            this.context.logRecorder().record(HttpLogEvent.COMPLETE, Map.copyOf(fields));
        }
    }

    private void requestLog(final ClientRequest request) {
        if (this.config.logging().enabled()) {
            final Map<String, Object> fields = this.fields();
            fields.put("method", request.method().name());
            fields.put("url", HttpLogs.url(request.url(), this.config.logging()));
            this.context.logRecorder().record(HttpLogEvent.REQUEST, Map.copyOf(fields));
        }
    }

    private void responseLog(final ClientResponse response) {
        if (this.config.logging().enabled()) {
            final Map<String, Object> fields = this.fields();
            fields.put("status", response.statusCode().value());
            this.context.logRecorder().record(HttpLogEvent.RESPONSE, Map.copyOf(fields));
        }
    }

    private Map<String, Object> fields() {
        final Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("client", this.context.clientName());
        fields.put("exchangeId", this.context.id());
        return fields;
    }
}
