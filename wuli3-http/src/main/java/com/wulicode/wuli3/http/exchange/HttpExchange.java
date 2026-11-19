package com.wulicode.wuli3.http.exchange;

import com.wulicode.wuli3.http.callback.HttpErrorHandler;
import com.wulicode.wuli3.http.callback.HttpRequestProcessor;
import com.wulicode.wuli3.http.callback.HttpResponseProcessor;
import com.wulicode.wuli3.http.internal.logging.HttpLogs;
import com.wulicode.wuli3.http.logging.HttpLogEvent;
import com.wulicode.wuli3.http.logging.HttpLogRecorder;
import com.wulicode.wuli3.http.webclient.WebClientConfig;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * 客户端内复用的无状态交换流程；单次订阅状态由独立上下文保存。
 *
 * @author 国杨 create on 2026/11/13 15:40
 */
public final class HttpExchange {

    private final WebClientConfig config;
    private final HttpLogRecorder recorder;
    private final List<HttpRequestProcessor> before;
    private final List<HttpResponseProcessor> after;
    private final List<HttpErrorHandler> errors;

    public HttpExchange(
            final WebClientConfig config,
            final HttpLogRecorder recorder,
            final List<HttpRequestProcessor> before,
            final List<HttpResponseProcessor> after,
            final List<HttpErrorHandler> errors) {
        this.config = config;
        this.recorder = recorder;
        this.before = List.copyOf(before);
        this.after = List.copyOf(after);
        this.errors = List.copyOf(errors);
    }

    public Mono<ClientResponse> exchange(final ClientRequest request, final ExchangeFunction next) {
        return Mono.defer(() -> {
            final HttpExchangeContext context = new HttpExchangeContext(this.config.name(), this.config.logging());
            return this.exchange(context, request, next);
        });
    }

    private Mono<ClientResponse> exchange(
            final HttpExchangeContext context, final ClientRequest request, final ExchangeFunction next) {
        context.request(request);
        Mono<ClientRequest> prepared = Mono.just(request);
        for (final var processor : this.before) {
            prepared = prepared.flatMap(value -> Mono.defer(() -> processor.process(context, value))
                    .doOnNext(context::request)
                    .switchIfEmpty(Mono.error(new IllegalStateException("请求回调不能返回空 Mono"))));
        }
        return prepared.flatMap(value -> {
                    final ClientRequest captured = context.captureRequest(value);
                    return Mono.defer(() -> next.exchange(captured));
                })
                .flatMap(response -> this.processResponse(context, response))
                .switchIfEmpty(Mono.error(new IllegalStateException("HTTP 交换未返回响应")))
                .doOnNext(response -> context.markDelivered())
                .doOnError(failure -> this.finish(context, SignalType.ON_ERROR, failure))
                .doFinally(signal -> {
                    // 响应交付后由正文终结，避免外层取消抢先输出汇总。
                    if (signal == SignalType.CANCEL && !context.delivered()) {
                        this.finish(context, signal, null);
                    }
                });
    }

    private Mono<ClientResponse> processResponse(final HttpExchangeContext context, final ClientResponse response) {
        context.response(response);
        Mono<ClientResponse> processed = Mono.just(response);
        for (final var processor : this.after) {
            processed = processed
                    .flatMap(value -> this.processAfter(context, value, processor))
                    .doOnNext(context::response);
        }
        return processed.map(value -> value.mutate()
                .body(body -> context.captureResponse(body, value.headers().asHttpHeaders())
                        .doOnError(failure -> this.finish(context, SignalType.ON_ERROR, failure))
                        .doFinally(signal -> this.finish(context, signal, null)))
                .build());
    }

    private Mono<ClientResponse> processAfter(
            final HttpExchangeContext context, final ClientResponse response, final HttpResponseProcessor processor) {
        final AtomicBoolean bodySubscribed = new AtomicBoolean();
        final ClientResponse guarded = response.mutate()
                .body(body -> body.doOnSubscribe(ignored -> bodySubscribed.set(true)))
                .build();
        // 失败或取消时清理尚未消费的响应；已开始消费的正文由回调链负责释放。
        return Mono.usingWhen(
                Mono.just(guarded),
                current -> Mono.defer(() -> processor.process(context, current))
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

    private void finish(final HttpExchangeContext context, final SignalType signal, final @Nullable Throwable failure) {
        if (!context.tryFinish()) {
            return;
        }
        if (failure != null) {
            for (final var handler : this.errors) {
                try {
                    handler.handle(context, failure);
                } catch (final RuntimeException | Error observerFailure) {
                    HttpLogs.suppress(failure, observerFailure);
                }
            }
        }
        if (this.config.logging().enabled()) {
            this.recorder.record(HttpLogEvent.COMPLETE, context.finishLog(signal, failure));
        }
    }
}
